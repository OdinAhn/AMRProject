package com.amrproject.dto.response.map;

import com.amrproject.domain.entity.map.Node;
import com.amrproject.domain.entity.map.NodeType;

import java.math.BigDecimal;

public record NodeResponse(
        Long id,
        Long mapVersionId,
        String code,
        BigDecimal x,
        BigDecimal y,
        NodeType nodeType
) {

    public static NodeResponse from(Node node) {
        return new NodeResponse(
                node.getId(),
                node.getMapVersion().getId(),
                node.getCode(),
                node.getX(),
                node.getY(),
                node.getNodeType()
        );
    }
}
