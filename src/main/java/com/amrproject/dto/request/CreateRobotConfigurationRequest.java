package com.amrproject.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateRobotConfigurationRequest(
        @NotNull @Positive BigDecimal maxPayloadKg,
        @NotNull @Positive BigDecimal maxSpeedMps,
        @NotNull @Positive BigDecimal batteryCapacityWh,
        @NotNull @Min(0) @Max(100) Integer chargingLowThresholdPct,
        @NotNull @Min(0) @Max(100) Integer chargingTargetPct
) {
}
