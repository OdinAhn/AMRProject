package com.amrproject.robot;

import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.LoadState;
import com.amrproject.domain.entity.robot.LocalizationState;
import com.amrproject.domain.entity.robot.MotionState;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotCommandStatus;
import com.amrproject.domain.entity.robot.RobotCommandType;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.site.Site;
import com.amrproject.repository.robot.CommandEventRepository;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotStateSnapshotRepository;
import com.amrproject.service.AlarmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 실제 저장소 없이(Docker 불필요) 가상 로봇의 명령 처리·장애 주입 로직만 검증한다.
 */
class SimulatorRobotGatewayTest {

    private final CommandEventRepository commandEventRepository = Mockito.mock(CommandEventRepository.class);
    private final RobotCurrentStateRepository robotCurrentStateRepository = Mockito.mock(RobotCurrentStateRepository.class);
    private final RobotStateSnapshotRepository robotStateSnapshotRepository = Mockito.mock(RobotStateSnapshotRepository.class);
    private final AlarmService alarmService = Mockito.mock(AlarmService.class);

    private SimulatorRobotGateway gateway;
    private Robot robot;
    private RobotCurrentState state;

    @BeforeEach
    void setUp() {
        gateway = new SimulatorRobotGateway(commandEventRepository, robotCurrentStateRepository, robotStateSnapshotRepository, alarmService);

        Site site = new Site("SITE-01", "테스트 물류센터");
        robot = new Robot("ROBOT-01", "1호기", null, null, site);
        ReflectionTestUtils.setField(robot, "id", 1L);

        state = new RobotCurrentState(robot);
        when(robotCurrentStateRepository.findById(any())).thenReturn(Optional.of(state));
        when(commandEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(robotStateSnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private RobotCommand newCommand() {
        return new RobotCommand("CMD-" + System.nanoTime(), null, robot, RobotCommandType.MOVE_TO, null);
    }

    @Test
    void 정상_디스패치는_SUCCEEDED로_끝나고_로봇은_다시_IDLE이_된다() {
        RobotCommand command = newCommand();

        gateway.dispatch(command);

        assertThat(command.getStatus()).isEqualTo(RobotCommandStatus.SUCCEEDED);
        assertThat(state.getMotionState()).isEqualTo(MotionState.IDLE);
        assertThat(state.getConnectionState()).isEqualTo(ConnectionState.ONLINE);
    }

    @Test
    void COMMAND_FAILURE를_주입하면_명령이_FAILED로_끝난다() {
        gateway.injectFault(robot.getId(), SimulatedFault.COMMAND_FAILURE);

        RobotCommand command = newCommand();
        gateway.dispatch(command);

        assertThat(command.getStatus()).isEqualTo(RobotCommandStatus.FAILED);
    }

    @Test
    void COMMUNICATION_LOSS를_주입하면_UNKNOWN으로_끝나고_연결상태가_OFFLINE이_된다() {
        gateway.injectFault(robot.getId(), SimulatedFault.COMMUNICATION_LOSS);

        RobotCommand command = newCommand();
        gateway.dispatch(command);

        assertThat(command.getStatus()).isEqualTo(RobotCommandStatus.UNKNOWN);
        assertThat(state.getConnectionState()).isEqualTo(ConnectionState.OFFLINE);
    }

    @Test
    void LOCALIZATION_LOSS를_주입하면_위치추정_상태가_LOST가_된다() {
        gateway.injectFault(robot.getId(), SimulatedFault.LOCALIZATION_LOSS);

        RobotCommand command = newCommand();
        gateway.dispatch(command);

        assertThat(command.getStatus()).isEqualTo(RobotCommandStatus.SUCCEEDED);
        assertThat(state.getLocalizationState()).isEqualTo(LocalizationState.LOST);
    }

    @Test
    void LOAD_SENSOR_MISMATCH를_주입하면_적재상태가_UNKNOWN이_된다() {
        gateway.injectFault(robot.getId(), SimulatedFault.LOAD_SENSOR_MISMATCH);

        RobotCommand command = newCommand();
        gateway.dispatch(command);

        assertThat(command.getStatus()).isEqualTo(RobotCommandStatus.SUCCEEDED);
        assertThat(state.getLoadState()).isEqualTo(LoadState.UNKNOWN);
    }

    @Test
    void 장애는_한번_디스패치에만_적용되고_다음_디스패치는_정상이다() {
        gateway.injectFault(robot.getId(), SimulatedFault.COMMAND_FAILURE);
        gateway.dispatch(newCommand());

        RobotCommand second = newCommand();
        gateway.dispatch(second);

        assertThat(second.getStatus()).isEqualTo(RobotCommandStatus.SUCCEEDED);
    }
}
