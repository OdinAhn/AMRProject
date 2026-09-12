package com.amrproject.domain.entity.robot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 로봇의 "현재" 상태만 담는 1:1 테이블 (docs/ERD.md §5 "robot_current_states").
 * 각 축은 독립적으로 전이된다 — docs/STATE_MACHINES.md §4.
 * current_node_id/pose는 지도 스키마(6단계) 전까지 보류.
 *
 * <p>robots와 PK를 공유하지만 연관관계(@OneToOne/@MapsId)로 매핑하지 않고
 * robot_id를 단순 스칼라 값으로만 갖는다 — @MapsId는 Spring Data의
 * save()가 merge()를 쓸 때 연관관계 해석 과정에서 충돌해 안전하지 않았다.
 */
@Entity
@Table(name = "robot_current_states")
public class RobotCurrentState {

    @Id
    @Column(name = "robot_id")
    private Long robotId;

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

    @Column(name = "state_version", nullable = false)
    private Integer stateVersion;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected RobotCurrentState() {
    }

    public RobotCurrentState(Robot robot) {
        this.robotId = robot.getId();
        this.connectionState = ConnectionState.OFFLINE;
        this.operationMode = OperationMode.MANUAL;
        this.motionState = MotionState.IDLE;
        this.safetyState = SafetyState.NORMAL;
        this.loadState = LoadState.EMPTY;
        this.localizationState = LocalizationState.LOCALIZED;
        this.batteryPct = 100;
        this.stateVersion = 0;
        Instant now = Instant.now();
        this.reportedAt = now;
        this.receivedAt = now;
    }

    /**
     * 시뮬레이터/실제 로봇이 보고한 값을 반영한다. 버전을 올려 재접속 후
     * 오래된 이벤트를 구분할 수 있게 한다 (docs/API.md §5).
     */
    public void report(
            ConnectionState connectionState,
            OperationMode operationMode,
            MotionState motionState,
            SafetyState safetyState,
            LoadState loadState,
            LocalizationState localizationState,
            Integer batteryPct,
            Instant reportedAt
    ) {
        this.connectionState = connectionState;
        this.operationMode = operationMode;
        this.motionState = motionState;
        this.safetyState = safetyState;
        this.loadState = loadState;
        this.localizationState = localizationState;
        this.batteryPct = batteryPct;
        this.stateVersion = this.stateVersion + 1;
        this.reportedAt = reportedAt;
        this.receivedAt = Instant.now();
    }

    public Long getRobotId() {
        return robotId;
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

    public Integer getStateVersion() {
        return stateVersion;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
