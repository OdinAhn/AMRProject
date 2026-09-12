package com.amrproject.domain.entity.task;

import com.amrproject.exception.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 참고: docs/STATE_MACHINES.md §2. target_node_id는 지도 스키마(6단계)가
 * 준비되기 전까지 의도적으로 생략했다 — docs/ERD.md §5 참고.
 */
@Entity
@Table(name = "task_steps", uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "step_no"}))
public class TaskStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 30)
    private TaskStepType stepType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStepStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TaskStep() {
    }

    public TaskStep(Task task, int stepNo, TaskStepType stepType) {
        this.task = task;
        this.stepNo = stepNo;
        this.stepType = stepType;
        this.status = TaskStepStatus.PENDING;
    }

    public void start() {
        requireStatus(TaskStepStatus.PENDING, "START");
        this.status = TaskStepStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void succeed() {
        requireStatus(TaskStepStatus.RUNNING, "SUCCEED");
        this.status = TaskStepStatus.SUCCEEDED;
        this.completedAt = Instant.now();
    }

    public void fail() {
        requireStatus(TaskStepStatus.RUNNING, "FAIL");
        this.status = TaskStepStatus.FAILED;
        this.completedAt = Instant.now();
    }

    public void retry() {
        requireStatus(TaskStepStatus.FAILED, "RETRY");
        this.status = TaskStepStatus.RUNNING;
        this.completedAt = null;
    }

    public void skip() {
        requireStatus(TaskStepStatus.PENDING, "SKIP");
        this.status = TaskStepStatus.SKIPPED;
        this.completedAt = Instant.now();
    }

    private void requireStatus(TaskStepStatus required, String action) {
        if (status != required) {
            throw new InvalidStateTransitionException(
                    "TASK_STEP_" + action + "_NOT_ALLOWED",
                    "단계 상태가 " + required + "가 아니어서(현재: " + status + ") " + action + "을(를) 수행할 수 없습니다."
            );
        }
    }

    public Long getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public Integer getStepNo() {
        return stepNo;
    }

    public TaskStepType getStepType() {
        return stepType;
    }

    public TaskStepStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
