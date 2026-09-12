package com.amrproject.dto.request.map;

import com.amrproject.domain.entity.map.ZoneType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateZoneRequest(
        @NotBlank String code,
        @NotNull ZoneType zoneType,
        @Positive Integer maxCapacity,
        @NotEmpty List<Long> nodeIds
) {
}
