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
 * 참고: docs/STATE_MACHINES.md §5. 시뮬레이터가 충전을 즉시 처리하므로
 * RESERVED는 사실상 순간적으로 지나가지만, 상태 다이어그램을 그대로 구현한다.
 */
@Entity
@Table(name = "charging_sessions")
public class ChargingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "robot_id", nullable = false)
    private Robot robot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargingSessionStatus status;

    @Column(name = "battery_start_pct", nullable = false)
    private Integer batteryStartPct;

    @Column(name = "battery_end_pct")
    private Integer batteryEndPct;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ChargingSession() {
    }

    public ChargingSession(Robot robot, Station station, Task task, Integer batteryStartPct) {
        this.robot = robot;
        this.station = station;
        this.task = task;
        this.batteryStartPct = batteryStartPct;
        this.status = ChargingSessionStatus.RESERVED;
    }

    public void startCharging() {
        requireStatus(ChargingSessionStatus.RESERVED, "START_CHARGING");
        this.status = ChargingSessionStatus.CHARGING;
        this.startedAt = Instant.now();
    }

    public void complete(Integer batteryEndPct) {
        requireStatus(ChargingSessionStatus.CHARGING, "COMPLETE");
        this.status = ChargingSessionStatus.COMPLETED;
        this.batteryEndPct = batteryEndPct;
        this.completedAt = Instant.now();
    }

    public void abort() {
        if (status != ChargingSessionStatus.RESERVED && status != ChargingSessionStatus.CHARGING) {
            throw new InvalidStateTransitionException(
                    "CHARGING_SESSION_ABORT_NOT_ALLOWED",
                    "충전 세션 상태(" + status + ")에서는 중단할 수 없습니다."
            );
        }
        this.status = ChargingSessionStatus.ABORTED;
        this.completedAt = Instant.now();
    }

    private void requireStatus(ChargingSessionStatus required, String action) {
        if (status != required) {
            throw new InvalidStateTransitionException(
                    "CHARGING_SESSION_" + action + "_NOT_ALLOWED",
                    "충전 세션 상태가 " + required + "가 아니어서(현재: " + status + ") " + action + "을(를) 수행할 수 없습니다."
            );
        }
    }

    public Long getId() {
        return id;
    }

    public Robot getRobot() {
        return robot;
    }

    public Station getStation() {
        return station;
    }

    public Task getTask() {
        return task;
    }

    public ChargingSessionStatus getStatus() {
        return status;
    }

    public Integer getBatteryStartPct() {
        return batteryStartPct;
    }

    public Integer getBatteryEndPct() {
        return batteryEndPct;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
