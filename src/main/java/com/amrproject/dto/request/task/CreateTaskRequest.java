package com.amrproject.dto.request.task;

import jakarta.validation.constraints.NotNull;

public record CreateTaskRequest(
        @NotNull Long originStationId,
        @NotNull Long destinationStationId
) {
}
