package com.amrproject.dto.request.task;

import jakarta.validation.constraints.NotNull;

public record CreateExternalTaskRequest(
        @NotNull Long originStationId,
        @NotNull Long destinationStationId,
        String externalRef
) {
}
