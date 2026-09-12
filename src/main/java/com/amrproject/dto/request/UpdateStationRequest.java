package com.amrproject.dto.request;

import com.amrproject.domain.entity.StationStatus;

public record UpdateStationRequest(
        String name,
        StationStatus status,
        Integer maxConcurrentRobots
) {
}
