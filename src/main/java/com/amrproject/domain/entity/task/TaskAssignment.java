package com.amrproject.domain.entity.task;

import com.amrproject.domain.entity.robot.Robot;
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
 * 참고: docs/STATE_MACHINES.md §8.
 */
@Entity
@Table(name = "task_assignments")
public class TaskAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "robot_id", nullable = false)
    private Robot robot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskAssignmentStatus status;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected TaskAssignment() {
    }

    public TaskAssignment(Task task, Robot robot) {
        this.task = task;
        this.robot = robot;
        this.status = TaskAssignmentStatus.ACTIVE;
        this.assignedAt = Instant.now();
    }

    public void release() {
        requireActive("RELEASE");
        this.status = TaskAssignmentStatus.RELEASED;
        this.releasedAt = Instant.now();
    }

    public void complete() {
        requireActive("COMPLETE");
        this.status = TaskAssignmentStatus.COMPLETED;
        this.releasedAt = Instant.now();
    }

    private void requireActive(String action) {
        if (status != TaskAssignmentStatus.ACTIVE) {
            throw new InvalidStateTransitionException(
                    "TASK_ASSIGNMENT_" + action + "_NOT_ALLOWED",
                    "배정 상태가 ACTIVE가 아니어서(현재: " + status + ") " + action + "을(를) 수행할 수 없습니다."
            );
        }
    }

    public Long getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public Robot getRobot() {
        return robot;
    }

    public TaskAssignmentStatus getStatus() {
        return status;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}
