package com.amrproject.service.robot;

import com.amrproject.domain.entity.alarm.AlarmSeverity;
import com.amrproject.domain.entity.alarm.AlarmSourceType;
import com.amrproject.domain.entity.alarm.AlarmType;
import com.amrproject.domain.entity.map.Node;
import com.amrproject.domain.entity.robot.CommandEvent;
import com.amrproject.domain.entity.robot.OperationMode;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotCommandStatus;
import com.amrproject.domain.entity.robot.RobotCommandType;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.robot.RobotStateSnapshot;
import com.amrproject.domain.entity.robot.SafetyState;
import com.amrproject.repository.map.NodeRepository;
import com.amrproject.repository.robot.CommandEventRepository;
import com.amrproject.repository.robot.RobotCommandRepository;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.repository.robot.RobotStateSnapshotRepository;
import com.amrproject.repository.site.StationRepository;
import com.amrproject.service.alarm.AlarmService;
import com.amrproject.service.map.MapRouteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 41단계: 실제 로봇(MQTT/VDA 5050)이 보고한 동작 모드·안전 상태·현재 위치·즉시 동작 결과를 서버 상태에 반영한다.
 *
 * <p>예전 MQTT 어댑터는 주문 결과와 배터리만 봐서, 실제 로봇은 서버에서 늘 MANUAL·안전 NORMAL·위치 모름으로 남았다 — 자동 배정 대상이
 * 되지 못했고(AUTO 필요), 비상정지 버튼을 눌러도 알람이 없었고, 가까운 로봇 선택(38단계)도 등록 순서로만 동작했다.
 * 프로토콜 필드 해석은 게이트웨이가 하고, 이 서비스는 도메인 값({@link Report})만 받는다.
 *
 * <p>잠금 순서: 명령 행 → 로봇 상태 행(명령 결과 반영과 같은 순서). 상태 행은 잠금 조회로 읽어 동시에 도는 명령 처리의 갱신을 덮어쓰지 않는다.
 */
@Service
public class RobotStateReportService {

    private static final Set<RobotCommandType> INSTANT_ACTION_TYPES = Set.of(RobotCommandType.EMERGENCY_STOP, RobotCommandType.CANCEL);

    private final RobotRepository robotRepository;
    private final RobotCurrentStateRepository robotCurrentStateRepository;
    private final RobotStateSnapshotRepository robotStateSnapshotRepository;
    private final RobotCommandRepository robotCommandRepository;
    private final CommandEventRepository commandEventRepository;
    private final StationRepository stationRepository;
    private final NodeRepository nodeRepository;
    private final MapRouteService mapRouteService;
    private final AlarmService alarmService;
    private final RobotStateEventRecorder robotStateEventRecorder;

    public RobotStateReportService(
            RobotRepository robotRepository,
            RobotCurrentStateRepository robotCurrentStateRepository,
            RobotStateSnapshotRepository robotStateSnapshotRepository,
            RobotCommandRepository robotCommandRepository,
            CommandEventRepository commandEventRepository,
            StationRepository stationRepository,
            NodeRepository nodeRepository,
            MapRouteService mapRouteService,
            AlarmService alarmService,
            RobotStateEventRecorder robotStateEventRecorder
    ) {
        this.robotRepository = robotRepository;
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.robotStateSnapshotRepository = robotStateSnapshotRepository;
        this.robotCommandRepository = robotCommandRepository;
        this.commandEventRepository = commandEventRepository;
        this.stationRepository = stationRepository;
        this.nodeRepository = nodeRepository;
        this.mapRouteService = mapRouteService;
        this.alarmService = alarmService;
        this.robotStateEventRecorder = robotStateEventRecorder;
    }

    /**
     * 보고에 없는 값(null)은 바꾸지 않는다. 모르는 로봇이거나 아직 상태 행이 없는 로봇(운영 전환·첫 명령 전)은 무시한다.
     */
    @Transactional
    public void apply(Report report) {
        Optional<Robot> found = robotRepository.findByCode(report.robotCode());
        if (found.isEmpty()) {
            return;
        }
        Robot robot = found.get();
        report.actionResults().forEach(result -> confirmInstantAction(robot, result));

        Optional<RobotCurrentState> locked = robotCurrentStateRepository.findByRobotIdForUpdate(robot.getId());
        if (locked.isEmpty()) {
            return;
        }
        RobotCurrentState state = locked.get();
        OperationMode mode = report.operationMode() != null ? report.operationMode() : state.getOperationMode();
        SafetyState safety = report.safetyState() != null ? report.safetyState() : state.getSafetyState();
        Long nodeId = report.lastNodeId() == null ? state.getCurrentNodeId()
                : resolveNodeId(robot, report.lastNodeId()).orElse(state.getCurrentNodeId());
        if (mode == state.getOperationMode() && safety == state.getSafetyState() && Objects.equals(nodeId, state.getCurrentNodeId())) {
            return;
        }

        SafetyState previousSafety = state.getSafetyState();
        state.report(state.getConnectionState(), mode, state.getMotionState(), safety, state.getLoadState(),
                state.getLocalizationState(), state.getBatteryPct(), Instant.now());
        state.arriveAt(nodeId);
        robotStateSnapshotRepository.save(new RobotStateSnapshot(robot, state));
        robotStateEventRecorder.recordChanged(robot.getId());
        reconcileSafetyAlarm(robot, previousSafety, safety);
    }

    /**
     * VDA 5050 노드 id를 활성 지도 노드로 바꾼다. 로봇이 지도 노드 코드를 보고하면 그 노드를, 이 서버가 주문에 넣은 작업장 코드
     * (MqttRobotGateway가 order의 nodeId로 작업장 코드를 보낸다)를 보고하면 그 작업장에 연결된 노드를 쓴다. 둘 다 아니면 위치를 바꾸지 않는다.
     */
    private Optional<Long> resolveNodeId(Robot robot, String reportedNodeId) {
        Long siteId = robot.getSite().getId();
        return mapRouteService.findActiveVersionId(siteId).flatMap(versionId -> nodeRepository.findByMapVersionId(versionId).stream()
                .filter(node -> node.getCode().equals(reportedNodeId))
                .map(Node::getId)
                .findFirst()
                .or(() -> stationRepository.findBySiteIdAndCode(siteId, reportedNodeId)
                        .flatMap(station -> mapRouteService.findStationNodeId(station.getId(), versionId))));
    }

    /** 즉시 동작(비상정지 startPause·주문 취소 cancelOrder)의 수행 결과로, 전달(ACCEPTED)에서 멈춰 있던 명령을 끝낸다. */
    private void confirmInstantAction(Robot robot, ActionResult result) {
        robotCommandRepository.findIdByCommandNo(result.actionId())
                .flatMap(robotCommandRepository::findByIdForUpdate)
                .filter(command -> command.getRobot().getId().equals(robot.getId()))
                .filter(command -> INSTANT_ACTION_TYPES.contains(command.getCommandType()))
                .filter(command -> command.getStatus() == RobotCommandStatus.ACCEPTED)
                .ifPresent(command -> {
                    command.startRunning();
                    if (result.finished()) {
                        command.succeed();
                    } else {
                        command.fail();
                    }
                    recordEvent(command, result.finished() ? "ACTION_FINISHED" : "ACTION_FAILED");
                });
    }

    /** 33단계 시뮬레이터와 같은 규칙: 비상정지 진입 시 CRITICAL 알람, 해제 보고 시 자동 해소. */
    private void reconcileSafetyAlarm(Robot robot, SafetyState previous, SafetyState current) {
        if (current == SafetyState.EMERGENCY_STOP && previous != SafetyState.EMERGENCY_STOP) {
            alarmService.raise(AlarmType.ROBOT_EMERGENCY_STOP, AlarmSeverity.CRITICAL, AlarmSourceType.ROBOT, robot.getId(),
                    "로봇이 비상정지되었습니다(현장 확인 후 해제 필요): " + robot.getCode());
        } else if (current != SafetyState.EMERGENCY_STOP && previous == SafetyState.EMERGENCY_STOP) {
            alarmService.autoResolve(AlarmType.ROBOT_EMERGENCY_STOP, AlarmSourceType.ROBOT, robot.getId());
        }
    }

    private void recordEvent(RobotCommand command, String eventType) {
        Instant now = Instant.now();
        commandEventRepository.save(new CommandEvent(command, eventType, null, now, now));
    }

    /**
     * 프로토콜과 무관한 로봇 상태 보고. operationMode/safetyState/lastNodeId가 null이면 보고에 없던 값이다.
     *
     * @param lastNodeId 로봇이 마지막으로 지난 노드(지도 노드 코드 또는 작업장 코드)
     */
    public record Report(
            String robotCode,
            OperationMode operationMode,
            SafetyState safetyState,
            String lastNodeId,
            List<ActionResult> actionResults
    ) {
    }

    /** @param finished true면 수행 완료, false면 수행 실패 */
    public record ActionResult(String actionId, boolean finished) {
    }
}
