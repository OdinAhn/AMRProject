package com.amrproject.dto.request.robot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRobotRequest(
        @NotBlank String code,
        @NotBlank String name,
        String model,
        String manufacturer,
        @NotNull Long siteId
) {
}
