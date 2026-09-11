package com.amrproject.dto.response;

import com.amrproject.domain.entity.UserStationAssignment;

import java.time.Instant;

public record StationAssignmentResponse(
        Long id,
        Long stationId,
        String stationCode,
        Instant createdAt
) {

    public static StationAssignmentResponse from(UserStationAssignment assignment) {
        return new StationAssignmentResponse(
                assignment.getId(),
                assignment.getStation().getId(),
                assignment.getStation().getCode(),
                assignment.getCreatedAt()
        );
    }
}
