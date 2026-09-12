package com.amrproject.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateMapRequest(
        @NotBlank String name
) {
}
