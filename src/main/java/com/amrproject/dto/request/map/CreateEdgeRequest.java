package com.amrproject.dto.request.map;

import com.amrproject.domain.entity.map.EdgeDirection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateEdgeRequest(
        @NotNull Long fromNodeId,
        @NotNull Long toNodeId,
        @NotNull EdgeDirection direction,
        BigDecimal distance,
        @Positive Integer maxCapacity
) {
}
