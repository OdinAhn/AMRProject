package com.amrproject.domain.entity;

import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotCommandStatus;
import com.amrproject.domain.entity.robot.RobotCommandType;
import com.amrproject.domain.entity.site.Site;
import com.amrproject.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * docs/STATE_MACHINES.md §3을 검증한다.
 */
class RobotCommandTest {

    private final Site site = new Site("SITE-01", "테스트 물류센터");
    private final Robot robot = new Robot("ROBOT-01", "1호기", null, null, site);

    private RobotCommand newCommand() {
        return new RobotCommand("CMD-1", null, robot, RobotCommandType.MOVE_TO, null);
    }

    @Test
    void 생성된_명령은_PENDING이다() {
        assertThat(newCommand().getStatus()).isEqualTo(RobotCommandStatus.PENDING);
    }

    @Test
    void 정상_흐름_PENDING_SENT_ACCEPTED_RUNNING_SUCCEEDED() {
        RobotCommand command = newCommand();

        command.send();
        assertThat(command.getStatus()).isEqualTo(RobotCommandStatus.SENT);

        command.accept();
        assertThat(command.getStatus()).isEqualTo(RobotCommandStatus.ACCEPTED);

        command.startRunning();
        assertThat(command.getStatus()).isEqualTo(RobotCommandStatus.RUNNING);

        command.succeed();
        assertThat(command.getStatus()).isEqualTo(RobotCommandStatus.SUCCEEDED);
        assertThat(command.getCompletedAt()).isNotNull();
    }

    @Test
    void SENT_상태에서_응답이_없으면_UNKNOWN으로_전이한다() {
        RobotCommand command = newCommand();
        command.send();

        command.markUnknown();

        assertThat(command.getStatus()).isEqualTo(RobotCommandStatus.UNKNOWN);
    }

    @Test
    void UNKNOWN에서_재연결후_RUNNING으로_복귀할_수_있다() {
        RobotCommand command = newCommand();
        command.send();
        command.markUnknown();

        command.resumeRunning();

        assertThat(command.getStatus()).isEqualTo(RobotCommandStatus.RUNNING);
    }

    @Test
    void UNKNOWN에서_재연결후_바로_SUCCEEDED로_확정할_수_있다() {
        RobotCommand command = newCommand();
        command.send();
        command.markUnknown();

        command.succeed();

        assertThat(command.getStatus()).isEqualTo(RobotCommandStatus.SUCCEEDED);
    }

    @Test
    void PENDING에서_바로_수락하면_예외() {
        assertThatThrownBy(() -> newCommand().accept())
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void 종료된_명령은_다시_전이할_수_없다() {
        RobotCommand command = newCommand();
        command.send();
        command.accept();
        command.startRunning();
        command.succeed();

        assertThatThrownBy(command::succeed)
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
