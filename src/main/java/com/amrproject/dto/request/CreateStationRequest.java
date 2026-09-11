package com.amrproject.dto.request;

import com.amrproject.domain.entity.StationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStationRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull StationType stationType
) {
}
