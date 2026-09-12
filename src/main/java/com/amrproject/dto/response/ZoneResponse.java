package com.amrproject.dto.response;

import com.amrproject.domain.entity.Node;
import com.amrproject.domain.entity.Zone;
import com.amrproject.domain.entity.ZoneType;

import java.util.List;

public record ZoneResponse(
        Long id,
        Long mapVersionId,
        String code,
        ZoneType zoneType,
        Integer maxCapacity,
        List<Long> nodeIds
) {

    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(
                zone.getId(),
                zone.getMapVersion().getId(),
                zone.getCode(),
                zone.getZoneType(),
                zone.getMaxCapacity(),
                zone.getNodes().stream().map(Node::getId).toList()
        );
    }
}
