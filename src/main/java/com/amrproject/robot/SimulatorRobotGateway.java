package com.amrproject.robot;

import com.amrproject.domain.entity.AlarmSeverity;
import com.amrproject.domain.entity.AlarmSourceType;
import com.amrproject.domain.entity.AlarmType;
import com.amrproject.domain.entity.CommandEvent;
import com.amrproject.domain.entity.ConnectionState;
import com.amrproject.domain.entity.LoadState;
import com.amrproject.domain.entity.LocalizationState;
import com.amrproject.domain.entity.MotionState;
import com.amrproject.domain.entity.OperationMode;
import com.amrproject.domain.entity.Robot;
import com.amrproject.domain.entity.RobotCommand;
import com.amrproject.domain.entity.RobotCommandType;
import com.amrproject.domain.entity.RobotConfiguration;
import com.amrproject.domain.entity.RobotCurrentState;
import com.amrproject.domain.entity.RobotStateSnapshot;
import com.amrproject.domain.entity.SafetyState;
import com.amrproject.repository.CommandEventRepository;
import com.amrproject.repository.RobotCurrentStateRepository;
import com.amrproject.repository.RobotStateSnapshotRepository;
import com.amrproject.service.AlarmService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 가상 로봇. 실제 네트워크 없이 인프로세스로 명령을 즉시 처리해
 * docs/STATE_MACHINES.md §3 상태 전이와 §4 상태 보고를 재현한다.
 *
 * <p>장애 주입({@link #injectFault})으로 예약된 장애는 해당 로봇에 대한
 * 다음 dispatch 한 번에만 적용되고 소비된다. 메모리에만 있는 값이라
 * 애플리케이션 재시작 시 초기화된다 — 테스트/운영 보조용이며 영속 데이터가 아니다.
 */
@Component
public class SimulatorRobotGateway implements RobotGateway {

    private final CommandEventRepository commandEventRepository;
    private final RobotCurrentStateRepository robotCurrentStateRepository;
    private final RobotStateSnapshotRepository robotStateSnapshotRepository;
    private final AlarmService alarmService;
    private final Map<Long, SimulatedFault> pendingFaults = new ConcurrentHashMap<>();

    public SimulatorRobotGateway(
            CommandEventRepository commandEventRepository,
            RobotCurrentStateRepository robotCurrentStateRepository,
            RobotStateSnapshotRepository robotStateSnapshotRepository,
            AlarmService alarmService
    ) {
        this.commandEventRepository = commandEventRepository;
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.robotStateSnapshotRepository = robotStateSnapshotRepository;
        this.alarmService = alarmService;
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
            command.markUnknown();
            recordCommandEvent(command, "TIMEOUT");
            report(robot, state, ConnectionState.OFFLINE, state.getOperationMode(), state.getMotionState(), state.getSafetyState(), localization, loadState, state.getBatteryPct());
            return;
        }

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
        report(robot, state, ConnectionState.ONLINE, OperationMode.AUTO, MotionState.IDLE, state.getSafetyState(), localization, loadState, batteryPct);
    }

    private int targetBatteryPct(Robot robot) {
        RobotConfiguration configuration = robot.getAppliedConfiguration();
        return configuration != null ? configuration.getChargingTargetPct() : 100;
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

        state.report(connection, mode, motion, safety, loadState, localization, batteryPct, Instant.now());
        robotStateSnapshotRepository.save(new RobotStateSnapshot(robot, state));

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
