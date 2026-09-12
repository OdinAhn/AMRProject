package com.amrproject.domain.entity;

import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.site.Site;
import com.amrproject.domain.entity.site.Station;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.task.TaskAssignment;
import com.amrproject.domain.entity.task.TaskStatus;
import com.amrproject.domain.entity.task.TaskType;
import com.amrproject.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * docs/STATE_MACHINES.md §1의 전이 표를 그대로 검증한다.
 */
class TaskTest {

    private final Site site = new Site("SITE-01", "테스트 물류센터");
    private final Station origin = new Station(site, "ST-01", "입고", StationType.PICKUP);
    private final Station destination = new Station(site, "ST-02", "출고", StationType.DROPOFF);
    private final Robot robot = new Robot("ROBOT-01", "1호기", null, null, site);

    private Task newTask() {
        return new Task("TASK-1", site, TaskType.TRANSPORT, 0, origin, destination, null, "idem-1", null);
    }

    @Test
    void 생성된_작업은_QUEUED_상태다() {
        assertThat(newTask().getStatus()).isEqualTo(TaskStatus.QUEUED);
    }

    @Test
    void QUEUED에서_배정하면_ASSIGNED가_된다() {
        Task task = newTask();
        TaskAssignment assignment = new TaskAssignment(task, robot);

        task.assign(assignment);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.ASSIGNED);
        assertThat(task.getCurrentAssignment()).isEqualTo(assignment);
    }

    @Test
    void ASSIGNED가_아닌_상태에서_배정하면_예외() {
        Task task = newTask();
        TaskAssignment assignment = new TaskAssignment(task, robot);
        task.assign(assignment);

        assertThatThrownBy(() -> task.assign(assignment))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void ASSIGNED에서_시작하면_RUNNING이_된다() {
        Task task = assignedTask();

        task.start();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void QUEUED에서_바로_시작하면_예외() {
        assertThatThrownBy(() -> newTask().start())
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void RUNNING에서_완료하면_SUCCEEDED이고_완료시각이_기록된다() {
        Task task = runningTask();

        task.succeed();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    void RUNNING에서_일시정지하고_재개할_수_있다() {
        Task task = runningTask();

        task.pause();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.PAUSED);

        task.resume();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void QUEUED에서_취소하면_즉시_CANCELED다() {
        Task task = newTask();

        task.requestCancel();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELED);
    }

    @Test
    void RUNNING에서_취소_요청하면_CANCEL_REQUESTED다() {
        Task task = runningTask();

        task.requestCancel();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCEL_REQUESTED);
    }

    @Test
    void 이미_취소된_작업을_다시_취소해도_예외가_아니다() {
        Task task = newTask();
        task.requestCancel();

        task.requestCancel();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELED);
    }

    @Test
    void 종료된_작업은_취소할_수_없다() {
        Task task = runningTask();
        task.succeed();

        assertThatThrownBy(task::requestCancel)
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void CANCEL_REQUESTED에서_확인하면_CANCELED다() {
        Task task = runningTask();
        task.requestCancel();

        task.confirmCanceled();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELED);
    }

    @Test
    void RUNNING에서_복구대기로_전이하고_재개할_수_있다() {
        Task task = runningTask();

        task.markRecoveryRequired();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RECOVERY_REQUIRED);

        task.resumeFromRecovery();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void 복구대기에서_최종_실패로_전이할_수_있다() {
        Task task = runningTask();
        task.markRecoveryRequired();

        task.failFromRecovery();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    void QUEUED에서는_복구대기로_전이할_수_없다() {
        assertThatThrownBy(() -> newTask().markRecoveryRequired())
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    private Task assignedTask() {
        Task task = newTask();
        task.assign(new TaskAssignment(task, robot));
        return task;
    }

    private Task runningTask() {
        Task task = assignedTask();
        task.start();
        return task;
    }
}
