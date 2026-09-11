package com.amrproject.domain.entity;

import com.amrproject.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * docs/STATE_MACHINES.md §2를 검증한다.
 */
class TaskStepTest {

    private final Site site = new Site("SITE-01", "테스트 물류센터");
    private final Station origin = new Station(site, "ST-01", "입고", StationType.PICKUP);
    private final Station destination = new Station(site, "ST-02", "출고", StationType.DROPOFF);
    private final Task task = new Task("TASK-1", site, TaskType.TRANSPORT, 0, origin, destination, null, "idem-1", null);

    @Test
    void 생성된_단계는_PENDING이다() {
        TaskStep step = new TaskStep(task, 1, TaskStepType.MOVE_TO_ORIGIN);
        assertThat(step.getStatus()).isEqualTo(TaskStepStatus.PENDING);
    }

    @Test
    void PENDING에서_시작하면_RUNNING이고_시작시각이_기록된다() {
        TaskStep step = new TaskStep(task, 1, TaskStepType.MOVE_TO_ORIGIN);

        step.start();

        assertThat(step.getStatus()).isEqualTo(TaskStepStatus.RUNNING);
        assertThat(step.getStartedAt()).isNotNull();
    }

    @Test
    void RUNNING에서_완료하면_SUCCEEDED다() {
        TaskStep step = new TaskStep(task, 1, TaskStepType.MOVE_TO_ORIGIN);
        step.start();

        step.succeed();

        assertThat(step.getStatus()).isEqualTo(TaskStepStatus.SUCCEEDED);
        assertThat(step.getCompletedAt()).isNotNull();
    }

    @Test
    void RUNNING에서_실패할_수_있다() {
        TaskStep step = new TaskStep(task, 1, TaskStepType.MOVE_TO_ORIGIN);
        step.start();

        step.fail();

        assertThat(step.getStatus()).isEqualTo(TaskStepStatus.FAILED);
    }

    @Test
    void PENDING이_아닌_단계는_시작할_수_없다() {
        TaskStep step = new TaskStep(task, 1, TaskStepType.MOVE_TO_ORIGIN);
        step.start();

        assertThatThrownBy(step::start).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void RUNNING이_아닌_단계는_완료할_수_없다() {
        TaskStep step = new TaskStep(task, 1, TaskStepType.MOVE_TO_ORIGIN);

        assertThatThrownBy(step::succeed).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void PENDING_단계는_건너뛸_수_있다() {
        TaskStep step = new TaskStep(task, 1, TaskStepType.MOVE_TO_ORIGIN);

        step.skip();

        assertThat(step.getStatus()).isEqualTo(TaskStepStatus.SKIPPED);
    }
}
