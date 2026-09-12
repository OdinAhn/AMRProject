package com.amrproject.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreateExternalTaskRequest(
        @NotNull Long originStationId,
        @NotNull Long destinationStationId,
        String externalRef
) {
}
