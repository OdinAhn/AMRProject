package com.amrproject.dto.response.map;

import com.amrproject.domain.entity.map.MapVersion;
import com.amrproject.domain.entity.map.MapVersionStatus;

import java.time.Instant;

public record MapVersionResponse(
        Long id,
        Long mapId,
        Integer versionNo,
        String filePath,
        String coordinateSystem,
        MapVersionStatus status,
        Instant validatedAt,
        Instant activatedAt,
        int nodeCount,
        int edgeCount,
        int zoneCount
) {

    public static MapVersionResponse from(MapVersion version, int nodeCount, int edgeCount, int zoneCount) {
        return new MapVersionResponse(
                version.getId(),
                version.getMap().getId(),
                version.getVersionNo(),
                version.getFilePath(),
                version.getCoordinateSystem(),
                version.getStatus(),
                version.getValidatedAt(),
                version.getActivatedAt(),
                nodeCount,
                edgeCount,
                zoneCount
        );
    }
}
