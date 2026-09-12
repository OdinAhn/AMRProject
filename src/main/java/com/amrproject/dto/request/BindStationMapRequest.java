package com.amrproject.dto.request;

import jakarta.validation.constraints.NotNull;

public record BindStationMapRequest(
        @NotNull Long mapVersionId,
        @NotNull Long nodeId
) {
}
