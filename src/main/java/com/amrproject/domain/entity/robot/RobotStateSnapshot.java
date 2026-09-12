package com.amrproject.domain.entity.robot;

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
 * 로봇 상태 이력 (append-only). docs/ERD.md §4.4 "robot_state_snapshots".
 */
@Entity
@Table(name = "robot_state_snapshots")
public class RobotStateSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "robot_id", nullable = false)
    private Robot robot;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_state", nullable = false, length = 20)
    private ConnectionState connectionState;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_mode", nullable = false, length = 20)
    private OperationMode operationMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "motion_state", nullable = false, length = 20)
    private MotionState motionState;

    @Enumerated(EnumType.STRING)
    @Column(name = "safety_state", nullable = false, length = 20)
    private SafetyState safetyState;

    @Enumerated(EnumType.STRING)
    @Column(name = "load_state", nullable = false, length = 20)
    private LoadState loadState;

    @Enumerated(EnumType.STRING)
    @Column(name = "localization_state", nullable = false, length = 20)
    private LocalizationState localizationState;

    @Column(name = "battery_pct", nullable = false)
    private Integer batteryPct;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected RobotStateSnapshot() {
    }

    public RobotStateSnapshot(Robot robot, RobotCurrentState state) {
        this.robot = robot;
        this.connectionState = state.getConnectionState();
        this.operationMode = state.getOperationMode();
        this.motionState = state.getMotionState();
        this.safetyState = state.getSafetyState();
        this.loadState = state.getLoadState();
        this.localizationState = state.getLocalizationState();
        this.batteryPct = state.getBatteryPct();
        this.reportedAt = state.getReportedAt();
        this.receivedAt = state.getReceivedAt();
    }

    public Long getId() {
        return id;
    }

    public Robot getRobot() {
        return robot;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public OperationMode getOperationMode() {
        return operationMode;
    }

    public MotionState getMotionState() {
        return motionState;
    }

    public SafetyState getSafetyState() {
        return safetyState;
    }

    public LoadState getLoadState() {
        return loadState;
    }

    public LocalizationState getLocalizationState() {
        return localizationState;
    }

    public Integer getBatteryPct() {
        return batteryPct;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
