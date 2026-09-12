package com.amrproject.dto.response.map;

import com.amrproject.domain.entity.map.Edge;
import com.amrproject.domain.entity.map.EdgeDirection;

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
