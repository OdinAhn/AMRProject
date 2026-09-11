package com.amrproject.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateSiteRequest(
        @NotBlank String code,
        @NotBlank String name
) {
}
