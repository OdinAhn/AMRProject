package com.amrproject.dto.response;

import com.amrproject.domain.entity.RobotConfiguration;

import java.math.BigDecimal;

public record RobotConfigurationResponse(
        Long id,
        Long robotId,
        Integer versionNo,
        BigDecimal maxPayloadKg,
        BigDecimal maxSpeedMps,
        BigDecimal batteryCapacityWh,
        Integer chargingLowThresholdPct,
        Integer chargingTargetPct
) {

    public static RobotConfigurationResponse from(RobotConfiguration configuration) {
        return new RobotConfigurationResponse(
                configuration.getId(),
                configuration.getRobot().getId(),
                configuration.getVersionNo(),
                configuration.getMaxPayloadKg(),
                configuration.getMaxSpeedMps(),
                configuration.getBatteryCapacityWh(),
                configuration.getChargingLowThresholdPct(),
                configuration.getChargingTargetPct()
        );
    }
}
