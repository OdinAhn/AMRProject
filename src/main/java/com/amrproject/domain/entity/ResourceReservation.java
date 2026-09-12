package com.amrproject.domain.entity;

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
 * 참고: docs/ERD.md §4.7, docs/STATE_MACHINES.md §6. resource_type/resource_id는
 * 다형성 참조라 DB FK 무결성은 없다 — 애플리케이션(TaskService)이 검증한다.
 * 7단계에서는 STATION만 실제로 사용한다 (EDGE/ZONE은 로봇 위치 추적이 생긴 뒤).
 * expires_at/EXPIRED는 전부 동기 처리라 아직 채우지 않는다.
 */
@Entity
@Table(name = "resource_reservations")
public class ResourceReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "robot_id", nullable = false)
    private Robot robot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_step_id")
    private TaskStep taskStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceReservationStatus status;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected ResourceReservation() {
    }

    public ResourceReservation(ResourceType resourceType, Long resourceId, Robot robot, TaskStep taskStep) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.robot = robot;
        this.taskStep = taskStep;
        this.status = ResourceReservationStatus.HELD;
        this.reservedAt = Instant.now();
    }

    public void release() {
        if (status != ResourceReservationStatus.HELD) {
            throw new InvalidStateTransitionException(
                    "RESOURCE_RESERVATION_RELEASE_NOT_ALLOWED",
                    "예약 상태가 HELD가 아니어서(현재: " + status + ") 해제할 수 없습니다."
            );
        }
        this.status = ResourceReservationStatus.RELEASED;
        this.releasedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public Robot getRobot() {
        return robot;
    }

    public TaskStep getTaskStep() {
        return taskStep;
    }

    public ResourceReservationStatus getStatus() {
        return status;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}
