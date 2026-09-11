package com.amrproject.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateTaskRequest(
        @NotNull Long originStationId,
        @NotNull Long destinationStationId
) {
}
