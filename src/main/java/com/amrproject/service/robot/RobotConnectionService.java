package com.amrproject.service.robot;

import com.amrproject.domain.entity.alarm.AlarmSeverity;
import com.amrproject.domain.entity.alarm.AlarmSourceType;
import com.amrproject.domain.entity.alarm.AlarmType;
import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.domain.entity.robot.RobotStateSnapshot;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.repository.robot.RobotStateSnapshotRepository;
import com.amrproject.service.alarm.AlarmService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 29단계: 로봇 연결 감시. 4~18단계에서는 로봇 상태가 명령을 보낼 때만 갱신돼, 대기 중인 로봇이
 * 꺼져도 계속 ONLINE으로 보였고 자동 배정 대상에도 남았다. 이제 서버가 마지막 수신 시각
 * ({@code robot_current_states.received_at})을 보고 하트비트가 끊긴 로봇을 OFFLINE으로 판정한다.
 *
 * <p>수신 시각을 갱신하는 경로는 게이트웨이마다 다르다.
 * <ul>
 *   <li>시뮬레이터: 실제 네트워크가 없어 {@link #heartbeatSimulatedRobots}가 대신 하트비트를 친다
 *       ({@link RobotConnectionScheduler}가 시뮬레이터 모드일 때만 호출). 통신 두절 장애를 주입한 로봇은 되살리지 않는다 —
 *       COMMUNICATION_LOSS 장애 주입은 기존처럼 다음 정상 명령에서만 복구된다.</li>
 *   <li>MQTT/VDA5050: 로봇이 보내는 모든 state 메시지가 {@link #receiveTelemetry}로 수신 시각을 갱신하고,
 *       OFFLINE이었으면 ONLINE으로 복구한다.</li>
 * </ul>
 * 연결 두절 알람({@code ROBOT_COMM_LOST})은 게이트웨이의 기존 발생·해소 규칙과 같은 종류·대상으로 올린다.
 * 상태 행 갱신은 모두 조건부 bulk update다(이유는 {@link RobotCurrentStateRepository} 참고).
 */
@Service
@Transactional(readOnly = true)
public class RobotConnectionService {

    private final RobotCurrentStateRepository robotCurrentStateRepository;
    private final RobotStateSnapshotRepository robotStateSnapshotRepository;
    private final RobotRepository robotRepository;
    private final AlarmService alarmService;
    private final RobotStateEventRecorder robotStateEventRecorder;
    private final Duration heartbeatTimeout;

    public RobotConnectionService(
            RobotCurrentStateRepository robotCurrentStateRepository,
            RobotStateSnapshotRepository robotStateSnapshotRepository,
            RobotRepository robotRepository,
            AlarmService alarmService,
            RobotStateEventRecorder robotStateEventRecorder,
            @Value("${app.robot.heartbeat-timeout-seconds:30}") long heartbeatTimeoutSeconds
    ) {
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.robotStateSnapshotRepository = robotStateSnapshotRepository;
        this.robotRepository = robotRepository;
        this.alarmService = alarmService;
        this.robotStateEventRecorder = robotStateEventRecorder;
        this.heartbeatTimeout = Duration.ofSeconds(heartbeatTimeoutSeconds);
    }

    /** 운영 중이고 ONLINE인데 마지막 수신이 타임아웃보다 오래된 로봇을 OFFLINE으로 바꾸고 알람을 올린다. 바뀐 로봇 id를 반환한다. */
    @Transactional
    public List<Long> markStaleRobotsOffline() {
        Instant threshold = Instant.now().minus(heartbeatTimeout);
        List<Long> changed = new ArrayList<>();
        for (Long robotId : robotCurrentStateRepository.findRobotIdsReceivedBefore(
                RobotLifecycleStatus.IN_SERVICE, ConnectionState.ONLINE, threshold)) {
            if (robotCurrentStateRepository.markOfflineIfStale(robotId, threshold) == 0) {
                continue; // 조회와 갱신 사이에 명령·텔레메트리가 들어와 더 이상 오래되지 않았다.
            }
            Robot robot = robotRepository.getReferenceById(robotId);
            recordSnapshot(robot);
            robotStateEventRecorder.recordChanged(robotId);
            alarmService.raise(AlarmType.ROBOT_COMM_LOST, AlarmSeverity.WARNING, AlarmSourceType.ROBOT, robotId,
                    "로봇 하트비트가 " + heartbeatTimeout.toSeconds() + "초 넘게 끊겼습니다: " + robot.getCode());
            changed.add(robotId);
        }
        return changed;
    }

    /**
     * 시뮬레이터 하트비트 — 운영 중인 로봇이 서버 안에서 계속 "살아 있게" 한다. ONLINE 로봇은 수신 시각만 갱신하고,
     * OFFLINE 로봇은 통신 두절 장애가 주입된 로봇(communicationLostRobotIds)이 아니면 ONLINE으로 복구한다.
     *
     * <p>복구가 필요한 이유(로컬 실행 중 발견): 서버가 하트비트 타임아웃보다 오래 꺼져 있다 다시 뜨면, 첫 감시 주기가
     * 첫 하트비트보다 먼저 돌아 모든 로봇을 OFFLINE으로 바꿀 수 있다. 시뮬레이터 로봇은 스스로 텔레메트리를 보내지
     * 않으므로 복구하지 않으면 영영 배정 대상에서 빠진다.
     */
    @Transactional
    public void heartbeatSimulatedRobots(Set<Long> communicationLostRobotIds) {
        Instant now = Instant.now();
        robotCurrentStateRepository.touchOnline(RobotLifecycleStatus.IN_SERVICE, now);
        for (Long robotId : robotCurrentStateRepository.findRobotIds(RobotLifecycleStatus.IN_SERVICE, ConnectionState.OFFLINE)) {
            if (communicationLostRobotIds.contains(robotId) || robotCurrentStateRepository.reviveIfOffline(robotId, now) == 0) {
                continue;
            }
            recordSnapshot(robotRepository.getReferenceById(robotId));
            robotStateEventRecorder.recordChanged(robotId);
            alarmService.autoResolve(AlarmType.ROBOT_COMM_LOST, AlarmSourceType.ROBOT, robotId);
        }
    }

    /**
     * 실제 로봇(MQTT)이 보낸 텔레메트리. 모르는 로봇 코드거나 아직 상태가 없는 로봇이면 무시한다
     * (상태 행은 첫 명령이나 IN_SERVICE 전환 때 만들어진다).
     */
    @Transactional
    public void receiveTelemetry(String robotCode, Integer batteryPct) {
        Optional<Robot> found = robotRepository.findByCode(robotCode);
        if (found.isEmpty()) {
            return;
        }
        Robot robot = found.get();
        Optional<RobotCurrentState> before = robotCurrentStateRepository.findById(robot.getId());
        if (before.isEmpty()) {
            return;
        }
        boolean wasOffline = before.get().getConnectionState() == ConnectionState.OFFLINE;
        robotCurrentStateRepository.receiveTelemetry(robot.getId(), batteryPct, Instant.now());
        boolean batteryChanged = batteryPct != null && !batteryPct.equals(before.get().getBatteryPct());
        if (wasOffline || batteryChanged) {
            robotStateEventRecorder.recordChanged(robot.getId());
        }
        if (wasOffline) {
            recordSnapshot(robot);
            alarmService.autoResolve(AlarmType.ROBOT_COMM_LOST, AlarmSourceType.ROBOT, robot.getId());
        }
    }

    /**
     * 45단계: 실제 로봇(MQTT)의 connection 토픽이 연결 끊김(OFFLINE·CONNECTIONBROKEN)을 알렸다. 하트비트 타임아웃을 기다리지 않고 바로
     * OFFLINE·통신 두절 알람으로 바꾼다. 다시 연결(ONLINE)되면 {@link #receiveTelemetry}와 같이 복구한다.
     */
    @Transactional
    public void receiveDisconnected(String robotCode, String connectionState) {
        Optional<Robot> found = robotRepository.findByCode(robotCode);
        if (found.isEmpty() || robotCurrentStateRepository.markOfflineIfOnline(found.get().getId()) == 0) {
            return;
        }
        Robot robot = found.get();
        recordSnapshot(robot);
        robotStateEventRecorder.recordChanged(robot.getId());
        String reason = "CONNECTIONBROKEN".equals(connectionState) ? "연결이 비정상적으로 끊겼습니다" : "연결을 종료했습니다";
        alarmService.raise(AlarmType.ROBOT_COMM_LOST, AlarmSeverity.WARNING, AlarmSourceType.ROBOT, robot.getId(),
                "로봇이 " + reason + "(MQTT connection " + connectionState + "): " + robot.getCode());
    }

    /** bulk update가 영속성 컨텍스트를 비우므로(clearAutomatically) 갱신된 행을 다시 읽어 이력을 남긴다. */
    private void recordSnapshot(Robot robot) {
        robotCurrentStateRepository.findById(robot.getId())
                .ifPresent(state -> robotStateSnapshotRepository.save(new RobotStateSnapshot(robot, state)));
    }
}
