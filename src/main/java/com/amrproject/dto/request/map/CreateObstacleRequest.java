package com.amrproject.dto.request.map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateObstacleRequest(
        @NotNull BigDecimal x,
        @NotNull BigDecimal y,
        @NotNull @Positive BigDecimal width,
        @NotNull @Positive BigDecimal height
) {
}
