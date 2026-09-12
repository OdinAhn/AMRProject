package com.amrproject.domain.entity.robot;

import com.amrproject.domain.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

/**
 * 참고: docs/ERD.md §4.4. 요청된 설정(robots.desired_config_id)과 실제 적용된 설정
 * (robots.applied_config_id)을 구분하기 위한 이력 row — 새 설정은 항상 새 row로 추가된다.
 */
@Entity
@Table(name = "robot_configurations", uniqueConstraints = @UniqueConstraint(columnNames = {"robot_id", "version_no"}))
public class RobotConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "robot_id", nullable = false)
    private Robot robot;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "max_payload_kg", nullable = false)
    private BigDecimal maxPayloadKg;

    @Column(name = "max_speed_mps", nullable = false)
    private BigDecimal maxSpeedMps;

    @Column(name = "battery_capacity_wh", nullable = false)
    private BigDecimal batteryCapacityWh;

    @Column(name = "charging_low_threshold_pct", nullable = false)
    private Integer chargingLowThresholdPct;

    @Column(name = "charging_target_pct", nullable = false)
    private Integer chargingTargetPct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    protected RobotConfiguration() {
    }

    public RobotConfiguration(
            Robot robot,
            Integer versionNo,
            BigDecimal maxPayloadKg,
            BigDecimal maxSpeedMps,
            BigDecimal batteryCapacityWh,
            Integer chargingLowThresholdPct,
            Integer chargingTargetPct,
            User createdBy
    ) {
        this.robot = robot;
        this.versionNo = versionNo;
        this.maxPayloadKg = maxPayloadKg;
        this.maxSpeedMps = maxSpeedMps;
        this.batteryCapacityWh = batteryCapacityWh;
        this.chargingLowThresholdPct = chargingLowThresholdPct;
        this.chargingTargetPct = chargingTargetPct;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Robot getRobot() {
        return robot;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public BigDecimal getMaxPayloadKg() {
        return maxPayloadKg;
    }

    public BigDecimal getMaxSpeedMps() {
        return maxSpeedMps;
    }

    public BigDecimal getBatteryCapacityWh() {
        return batteryCapacityWh;
    }

    public Integer getChargingLowThresholdPct() {
        return chargingLowThresholdPct;
    }

    public Integer getChargingTargetPct() {
        return chargingTargetPct;
    }

    public User getCreatedBy() {
        return createdBy;
    }
}
