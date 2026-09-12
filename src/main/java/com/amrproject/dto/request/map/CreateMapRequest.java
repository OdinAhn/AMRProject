package com.amrproject.dto.request.map;

import jakarta.validation.constraints.NotBlank;

public record CreateMapRequest(
        @NotBlank String name
) {
}
