package com.amrproject.robot;

import com.amrproject.domain.entity.CommandEvent;
import com.amrproject.domain.entity.ConnectionState;
import com.amrproject.domain.entity.LocalizationState;
import com.amrproject.domain.entity.MotionState;
import com.amrproject.domain.entity.OperationMode;
import com.amrproject.domain.entity.Robot;
import com.amrproject.domain.entity.RobotCommand;
import com.amrproject.domain.entity.RobotConfiguration;
import com.amrproject.domain.entity.RobotCurrentState;
import com.amrproject.domain.entity.RobotStateSnapshot;
import com.amrproject.domain.entity.SafetyState;
import com.amrproject.repository.CommandEventRepository;
import com.amrproject.repository.RobotCurrentStateRepository;
import com.amrproject.repository.RobotStateSnapshotRepository;
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
    private final Map<Long, SimulatedFault> pendingFaults = new ConcurrentHashMap<>();

    public SimulatorRobotGateway(
            CommandEventRepository commandEventRepository,
            RobotCurrentStateRepository robotCurrentStateRepository,
            RobotStateSnapshotRepository robotStateSnapshotRepository
    ) {
        this.commandEventRepository = commandEventRepository;
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.robotStateSnapshotRepository = robotStateSnapshotRepository;
    }

    @Override
    public void dispatch(RobotCommand command) {
        Robot robot = command.getRobot();
        RobotCurrentState state = currentStateOf(robot);
        SimulatedFault fault = pendingFaults.remove(robot.getId());

        command.send();
        recordCommandEvent(command, "SENT");

        if (fault == SimulatedFault.COMMUNICATION_LOSS) {
            command.markUnknown();
            recordCommandEvent(command, "TIMEOUT");
            report(robot, state, ConnectionState.OFFLINE, state.getOperationMode(), state.getMotionState(), state.getSafetyState());
            return;
        }

        command.accept();
        recordCommandEvent(command, "ACCEPTED");

        command.startRunning();
        report(robot, state, ConnectionState.ONLINE, OperationMode.AUTO, MotionState.MOVING, state.getSafetyState());
        recordCommandEvent(command, "PROGRESS");

        if (fault == SimulatedFault.COMMAND_FAILURE) {
            command.fail();
            recordCommandEvent(command, "FAILED");
            report(robot, state, ConnectionState.ONLINE, OperationMode.AUTO, MotionState.IDLE, state.getSafetyState());
            return;
        }

        command.succeed();
        recordCommandEvent(command, "SUCCEEDED");
        report(robot, state, ConnectionState.ONLINE, OperationMode.AUTO, MotionState.IDLE, state.getSafetyState());
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

    private void report(Robot robot, RobotCurrentState state, ConnectionState connection, OperationMode mode, MotionState motion, SafetyState safety) {
        state.report(
                connection,
                mode,
                motion,
                safety,
                state.getLoadState(),
                LocalizationState.LOCALIZED,
                state.getBatteryPct(),
                Instant.now()
        );
        robotStateSnapshotRepository.save(new RobotStateSnapshot(robot, state));
    }

    private void recordCommandEvent(RobotCommand command, String eventType) {
        Instant now = Instant.now();
        commandEventRepository.save(new CommandEvent(command, eventType, null, now, now));
    }
}
