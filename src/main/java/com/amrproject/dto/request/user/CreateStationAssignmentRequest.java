package com.amrproject.dto.request.user;

import jakarta.validation.constraints.NotNull;

public record CreateStationAssignmentRequest(
        @NotNull Long stationId
) {
}
