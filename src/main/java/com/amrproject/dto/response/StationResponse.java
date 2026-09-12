package com.amrproject.dto.response;

import com.amrproject.domain.entity.Station;
import com.amrproject.domain.entity.StationStatus;
import com.amrproject.domain.entity.StationType;

import java.time.Instant;

public record StationResponse(
        Long id,
        Long siteId,
        String code,
        String name,
        StationType stationType,
        StationStatus status,
        Integer maxConcurrentRobots,
        Instant createdAt,
        Instant updatedAt
) {

    public static StationResponse from(Station station) {
        return new StationResponse(
                station.getId(),
                station.getSite().getId(),
                station.getCode(),
                station.getName(),
                station.getStationType(),
                station.getStatus(),
                station.getMaxConcurrentRobots(),
                station.getCreatedAt(),
                station.getUpdatedAt()
        );
    }
}
