package com.amrproject.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateStationAssignmentRequest(
        @NotNull Long stationId
) {
}
