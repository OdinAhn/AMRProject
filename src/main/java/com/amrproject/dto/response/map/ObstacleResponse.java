package com.amrproject.dto.response.map;

import com.amrproject.domain.entity.map.Obstacle;

import java.math.BigDecimal;

public record ObstacleResponse(
        Long id,
        Long mapVersionId,
        BigDecimal x,
        BigDecimal y,
        BigDecimal width,
        BigDecimal height
) {

    public static ObstacleResponse from(Obstacle obstacle) {
        return new ObstacleResponse(
                obstacle.getId(),
                obstacle.getMapVersion().getId(),
                obstacle.getX(),
                obstacle.getY(),
                obstacle.getWidth(),
                obstacle.getHeight()
        );
    }
}
