package com.amrproject.dto.request.site;

import com.amrproject.domain.entity.site.StationStatus;

public record UpdateStationRequest(
        String name,
        StationStatus status,
        Integer maxConcurrentRobots
) {
}
