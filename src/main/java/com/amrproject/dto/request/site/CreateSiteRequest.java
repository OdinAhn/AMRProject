package com.amrproject.dto.request.site;

import jakarta.validation.constraints.NotBlank;

public record CreateSiteRequest(
        @NotBlank String code,
        @NotBlank String name
) {
}
