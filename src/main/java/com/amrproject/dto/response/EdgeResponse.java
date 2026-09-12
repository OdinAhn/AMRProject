package com.amrproject.dto.response;

import com.amrproject.domain.entity.Edge;
import com.amrproject.domain.entity.EdgeDirection;

import java.math.BigDecimal;

public record EdgeResponse(
        Long id,
        Long mapVersionId,
        Long fromNodeId,
        Long toNodeId,
        EdgeDirection direction,
        BigDecimal distance,
        Integer maxCapacity
) {

    public static EdgeResponse from(Edge edge) {
        return new EdgeResponse(
                edge.getId(),
                edge.getMapVersion().getId(),
                edge.getFromNode().getId(),
                edge.getToNode().getId(),
                edge.getDirection(),
                edge.getDistance(),
                edge.getMaxCapacity()
        );
    }
}
