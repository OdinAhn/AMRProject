package com.amrproject.robot;

import com.amrproject.domain.entity.alarm.AlarmSeverity;
import com.amrproject.domain.entity.alarm.AlarmSourceType;
import com.amrproject.domain.entity.alarm.AlarmType;
import com.amrproject.domain.entity.robot.CommandEvent;
import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.LoadState;
import com.amrproject.domain.entity.robot.LocalizationState;
import com.amrproject.domain.entity.robot.MotionState;
import com.amrproject.domain.entity.robot.OperationMode;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotCommandType;
import com.amrproject.domain.entity.robot.RobotConfiguration;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.robot.RobotStateSnapshot;
import com.amrproject.domain.entity.robot.SafetyState;
import com.amrproject.repository.robot.CommandEventRepository;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotStateSnapshotRepository;
import com.amrproject.domain.entity.site.Station;
import com.amrproject.domain.entity.task.TaskStep;
import com.amrproject.service.alarm.AlarmService;
import com.amrproject.service.map.MapRouteService;
import com.amrproject.service.robot.RobotStateEventRecorder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 가상 로봇. 실제 네트워크 없이 인프로세스로 명령을 즉시 처리해
 * docs/STATE_MACHINES.md §3 상태 전이와 §4 상태 보고를 재현한다.
 *
 * <p>장애 주입({@link #injectFault})으로 예약된 장애는 해당 로봇에 대한
 * 다음 dispatch 한 번에만 적용되고 소비된다. 메모리에만 있는 값이라
 * 애플리케이션 재시작 시 초기화된다 — 테스트/운영 보조용이며 영속 데이터가 아니다.
 *
 * <p>30단계: 이동 명령이 성공하면 목적지 작업장이 연결된 활성 지도 노드로 {@code current_node_id}를 갱신하고,
 * 이전 노드에서 통로 그래프 최단 경로를 따라 움직이는 모습을 {@link SimulatedMotionPublisher}로 화면에 흘려보낸다(표시 전용 —
 * 명령은 지금처럼 즉시 완료된다). 상태 축이 바뀔 때마다 {@code robot.state_changed} 이벤트도 남긴다.
 */
@Component
public class SimulatorRobotGateway implements RobotGateway {

    private final CommandEventRepository commandEventRepository;
    private final RobotCurrentStateRepository robotCurrentStateRepository;
    private final RobotStateSnapshotRepository robotStateSnapshotRepository;
    private final AlarmService alarmService;
    private final MapRouteService mapRouteService;
    private final SimulatedMotionPublisher simulatedMotionPublisher;
    private final RobotStateEventRecorder robotStateEventRecorder;
    private final Map<Long, SimulatedFault> pendingFaults = new ConcurrentHashMap<>();
    /** 29단계: COMMUNICATION_LOSS 장애로 끊긴 로봇 — 다음 정상 명령 전까지 시뮬레이터 하트비트가 되살리지 않는다(메모리 전용). */
    private final Set<Long> communicationLostRobotIds = ConcurrentHashMap.newKeySet();

    public SimulatorRobotGateway(
            CommandEventRepository commandEventRepository,
            RobotCurrentStateRepository robotCurrentStateRepository,
            RobotStateSnapshotRepository robotStateSnapshotRepository,
            AlarmService alarmService,
            MapRouteService mapRouteService,
            SimulatedMotionPublisher simulatedMotionPublisher,
            RobotStateEventRecorder robotStateEventRecorder
    ) {
        this.commandEventRepository = commandEventRepository;
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.robotStateSnapshotRepository = robotStateSnapshotRepository;
        this.alarmService = alarmService;
        this.mapRouteService = mapRouteService;
        this.simulatedMotionPublisher = simulatedMotionPublisher;
        this.robotStateEventRecorder = robotStateEventRecorder;
    }

    @Override
    public void dispatch(RobotCommand command) {
        Robot robot = command.getRobot();
        RobotCurrentState state = currentStateOf(robot);
        SimulatedFault fault = pendingFaults.remove(robot.getId());
        boolean isCharging = command.getCommandType() == RobotCommandType.START_CHARGE;
        MotionState progressMotion = isCharging ? MotionState.CHARGING : MotionState.MOVING;
        LocalizationState localization = fault == SimulatedFault.LOCALIZATION_LOSS ? LocalizationState.LOST : LocalizationState.LOCALIZED;
        LoadState loadState = fault == SimulatedFault.LOAD_SENSOR_MISMATCH ? LoadState.UNKNOWN : LoadState.EMPTY;

        command.send();
        recordCommandEvent(command, "SENT");

        if (fault == SimulatedFault.COMMUNICATION_LOSS) {
            communicationLostRobotIds.add(robot.getId());
            command.markUnknown();
            recordCommandEvent(command, "TIMEOUT");
            report(robot, state, ConnectionState.OFFLINE, state.getOperationMode(), state.getMotionState(), state.getSafetyState(), localization, loadState, state.getBatteryPct());
            return;
        }

        communicationLostRobotIds.remove(robot.getId());
        command.accept();
        recordCommandEvent(command, "ACCEPTED");

        command.startRunning();
        report(robot, state, ConnectionState.ONLINE, OperationMode.AUTO, progressMotion, state.getSafetyState(), localization, loadState, state.getBatteryPct());
        recordCommandEvent(command, "PROGRESS");

        if (fault == SimulatedFault.COMMAND_FAILURE) {
            command.fail();
            recordCommandEvent(command, "FAILED");
            report(robot, state, ConnectionState.ONLINE, OperationMode.AUTO, MotionState.IDLE, state.getSafetyState(), localization, loadState, state.getBatteryPct());
            return;
        }

        command.succeed();
        recordCommandEvent(command, "SUCCEEDED");
        int batteryPct = isCharging ? targetBatteryPct(robot) : state.getBatteryPct();
        if (command.getCommandType() == RobotCommandType.MOVE_TO) {
            arriveAtTargetNode(robot, state, command);
        }
        report(robot, state, ConnectionState.ONLINE, OperationMode.AUTO, MotionState.IDLE, state.getSafetyState(), localization, loadState, batteryPct);
    }

    /**
     * 30단계: 목적지 작업장이 활성 지도의 노드에 연결돼 있으면 그 노드로 위치를 옮기고, 이전 노드가 있으면 경로 애니메이션을 건다.
     * 활성 지도나 작업장 바인딩이 없으면 위치를 모르는 것으로 두고(null) 명령 결과에는 영향을 주지 않는다.
     */
    private void arriveAtTargetNode(Robot robot, RobotCurrentState state, RobotCommand command) {
        Station target = resolveTargetStation(command.getTaskStep());
        if (target == null) {
            return;
        }
        mapRouteService.findActiveVersionId(robot.getSite().getId()).ifPresentOrElse(versionId -> {
            Long targetNodeId = mapRouteService.findStationNodeId(target.getId(), versionId).orElse(null);
            Long previousNodeId = state.getCurrentNodeId();
            state.arriveAt(targetNodeId);
            if (previousNodeId != null && targetNodeId != null && !previousNodeId.equals(targetNodeId)) {
                simulatedMotionPublisher.animateAfterCommit(robot.getId(), versionId,
                        mapRouteService.shortestPath(versionId, previousNodeId, targetNodeId));
            }
        }, () -> state.arriveAt(null));
    }

    private Station resolveTargetStation(TaskStep step) {
        if (step == null) {
            return null;
        }
        return switch (step.getStepType()) {
            case MOVE_TO_ORIGIN -> step.getTask().getOriginStation();
            case MOVE_TO_DESTINATION, MOVE_TO_CHARGING_STATION -> step.getTask().getDestinationStation();
            default -> null;
        };
    }

    private int targetBatteryPct(Robot robot) {
        RobotConfiguration configuration = robot.getAppliedConfiguration();
        return configuration != null ? configuration.getChargingTargetPct() : 100;
    }

    /** 29단계: 통신 두절 장애가 적용된 뒤 아직 정상 명령을 받지 못한 로봇 id(시뮬레이터 하트비트 제외 대상). */
    public Set<Long> communicationLostRobotIds() {
        return Set.copyOf(communicationLostRobotIds);
    }

    /** 다음 dispatch 한 번에 적용될 장애를 예약한다. */
    public void injectFault(Long robotId, SimulatedFault fault) {
        pendingFaults.put(robotId, fault);
    }

    /**
     * 실제 로봇 통신이 없어 요청 즉시 적용 성공으로 처리한다 (4단계 명령 처리와 동일한 방식).
     */
    @Override
    public void applyConfiguration(RobotConfiguration configuration) {
        configuration.getRobot().markConfigurationApplied(configuration);
    }

    private RobotCurrentState currentStateOf(Robot robot) {
        return robotCurrentStateRepository.findById(robot.getId())
                .orElseGet(() -> robotCurrentStateRepository.save(new RobotCurrentState(robot)));
    }

    private void report(
            Robot robot, RobotCurrentState state, ConnectionState connection, OperationMode mode, MotionState motion,
            SafetyState safety, LocalizationState localization, LoadState loadState, int batteryPct
    ) {
        ConnectionState previousConnection = state.getConnectionState();
        LocalizationState previousLocalization = state.getLocalizationState();
        LoadState previousLoadState = state.getLoadState();
        MotionState previousMotion = state.getMotionState();
        Integer previousBattery = state.getBatteryPct();

        state.report(connection, mode, motion, safety, loadState, localization, batteryPct, Instant.now());
        robotStateSnapshotRepository.save(new RobotStateSnapshot(robot, state));
        if (previousConnection != connection || previousMotion != motion || previousLocalization != localization
                || previousLoadState != loadState || !previousBattery.equals(batteryPct)) {
            robotStateEventRecorder.recordChanged(robot.getId());
        }

        reconcileAlarms(robot, previousConnection, connection, previousLocalization, localization, previousLoadState, loadState);
    }

    /**
     * 14단계: 로봇 상태 축이 정상↔이상으로 전이될 때 알람을 발생/자동 해소한다
     * (docs/STATE_MACHINES.md §7). 관리자 확인이 필요 없는 일시적 이상은 다음 정상
     * dispatch에서 자동 해소된다.
     */
    private void reconcileAlarms(
            Robot robot,
            ConnectionState previousConnection, ConnectionState connection,
            LocalizationState previousLocalization, LocalizationState localization,
            LoadState previousLoadState, LoadState loadState
    ) {
        if (connection == ConnectionState.OFFLINE && previousConnection != ConnectionState.OFFLINE) {
            alarmService.raise(AlarmType.ROBOT_COMM_LOST, AlarmSeverity.WARNING, AlarmSourceType.ROBOT, robot.getId(),
                    "로봇 통신이 두절되었습니다: " + robot.getCode());
        } else if (connection != ConnectionState.OFFLINE && previousConnection == ConnectionState.OFFLINE) {
            alarmService.autoResolve(AlarmType.ROBOT_COMM_LOST, AlarmSourceType.ROBOT, robot.getId());
        }

        if (localization == LocalizationState.LOST && previousLocalization != LocalizationState.LOST) {
            alarmService.raise(AlarmType.LOCALIZATION_LOST, AlarmSeverity.WARNING, AlarmSourceType.ROBOT, robot.getId(),
                    "로봇 위치 추정에 실패했습니다: " + robot.getCode());
        } else if (localization != LocalizationState.LOST && previousLocalization == LocalizationState.LOST) {
            alarmService.autoResolve(AlarmType.LOCALIZATION_LOST, AlarmSourceType.ROBOT, robot.getId());
        }

        if (loadState == LoadState.UNKNOWN && previousLoadState != LoadState.UNKNOWN) {
            alarmService.raise(AlarmType.LOAD_EXCEPTION, AlarmSeverity.WARNING, AlarmSourceType.ROBOT, robot.getId(),
                    "로봇 적재 센서 응답이 불확실합니다: " + robot.getCode());
        } else if (loadState != LoadState.UNKNOWN && previousLoadState == LoadState.UNKNOWN) {
            alarmService.autoResolve(AlarmType.LOAD_EXCEPTION, AlarmSourceType.ROBOT, robot.getId());
        }
    }

    private void recordCommandEvent(RobotCommand command, String eventType) {
        Instant now = Instant.now();
        commandEventRepository.save(new CommandEvent(command, eventType, null, now, now));
    }
}
