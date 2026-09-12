package com.amrproject.domain.entity.task;

import com.amrproject.domain.entity.BaseAuditableEntity;
import com.amrproject.domain.entity.site.Site;
import com.amrproject.domain.entity.site.Station;
import com.amrproject.domain.entity.user.User;
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
import java.time.Instant;

/**
 * 상태 전이 규칙은 docs/STATE_MACHINES.md §1과 정확히 일치해야 한다.
 */
@Entity
@Table(name = "tasks")
public class Task extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_no", nullable = false, unique = true, length = 50)
    private String taskNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    private TaskType taskType;

    @Column(nullable = false)
    private Integer priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_station_id", nullable = false)
    private Station originStation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_station_id", nullable = false)
    private Station destinationStation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "external_ref", length = 100)
    private String externalRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_assignment_id")
    private TaskAssignment currentAssignment;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Task() {
    }

    public Task(
            String taskNo,
            Site site,
            TaskType taskType,
            int priority,
            Station originStation,
            Station destinationStation,
            User requestedBy,
            String idempotencyKey,
            String externalRef
    ) {
        this.taskNo = taskNo;
        this.site = site;
        this.taskType = taskType;
        this.priority = priority;
        this.originStation = originStation;
        this.destinationStation = destinationStation;
        this.requestedBy = requestedBy;
        this.idempotencyKey = idempotencyKey;
        this.externalRef = externalRef;
        this.status = TaskStatus.QUEUED;
    }

    public void assign(TaskAssignment assignment) {
        requireStatus(TaskStatus.QUEUED, "ASSIGN");
        this.currentAssignment = assignment;
        this.status = TaskStatus.ASSIGNED;
    }

    public void start() {
        requireStatus(TaskStatus.ASSIGNED, "START");
        this.status = TaskStatus.RUNNING;
    }

    public void succeed() {
        requireStatus(TaskStatus.RUNNING, "SUCCEED");
        this.status = TaskStatus.SUCCEEDED;
        this.completedAt = Instant.now();
    }

    public void pause() {
        requireStatus(TaskStatus.RUNNING, "PAUSE");
        this.status = TaskStatus.PAUSED;
    }

    public void resume() {
        requireStatus(TaskStatus.PAUSED, "RESUME");
        this.status = TaskStatus.RUNNING;
    }

    /**
     * 이미 취소된(또는 취소 요청된) 작업에 대한 재요청은 예외 없이 조용히 무시한다 (멱등성).
     */
    public void requestCancel() {
        if (status == TaskStatus.CANCELED || status == TaskStatus.CANCEL_REQUESTED) {
            return;
        }
        if (status == TaskStatus.QUEUED) {
            this.status = TaskStatus.CANCELED;
            this.completedAt = Instant.now();
            return;
        }
        if (status == TaskStatus.ASSIGNED || status == TaskStatus.RUNNING || status == TaskStatus.PAUSED) {
            this.status = TaskStatus.CANCEL_REQUESTED;
            return;
        }
        throw new InvalidStateTransitionException(
                "TASK_CANCEL_NOT_ALLOWED",
                "현재 상태(" + status + ")에서는 취소할 수 없습니다."
        );
    }

    public void confirmCanceled() {
        requireStatus(TaskStatus.CANCEL_REQUESTED, "CONFIRM_CANCELED");
        this.status = TaskStatus.CANCELED;
        this.completedAt = Instant.now();
    }

    public void markRecoveryRequired() {
        if (status != TaskStatus.ASSIGNED && status != TaskStatus.RUNNING && status != TaskStatus.CANCEL_REQUESTED) {
            throw new InvalidStateTransitionException(
                    "TASK_RECOVERY_NOT_ALLOWED",
                    "현재 상태(" + status + ")에서는 복구 대기 상태로 전이할 수 없습니다."
            );
        }
        this.status = TaskStatus.RECOVERY_REQUIRED;
    }

    public void resumeFromRecovery() {
        requireStatus(TaskStatus.RECOVERY_REQUIRED, "RESUME_FROM_RECOVERY");
        this.status = TaskStatus.RUNNING;
    }

    public void failFromRecovery() {
        requireStatus(TaskStatus.RECOVERY_REQUIRED, "FAIL_FROM_RECOVERY");
        this.status = TaskStatus.FAILED;
        this.completedAt = Instant.now();
    }

    private void requireStatus(TaskStatus required, String action) {
        if (status != required) {
            throw new InvalidStateTransitionException(
                    "TASK_" + action + "_NOT_ALLOWED",
                    "작업 상태가 " + required + "가 아니어서(현재: " + status + ") " + action + "을(를) 수행할 수 없습니다."
            );
        }
    }

    public Long getId() {
        return id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public Site getSite() {
        return site;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public Integer getPriority() {
        return priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Station getOriginStation() {
        return originStation;
    }

    public Station getDestinationStation() {
        return destinationStation;
    }

    public User getRequestedBy() {
        return requestedBy;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public TaskAssignment getCurrentAssignment() {
        return currentAssignment;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
