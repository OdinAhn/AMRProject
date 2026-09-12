package com.amrproject.dto.request.site;

import com.amrproject.domain.entity.site.StationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStationRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull StationType stationType
) {
}
