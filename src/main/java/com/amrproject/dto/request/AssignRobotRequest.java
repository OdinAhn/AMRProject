package com.amrproject.dto.request;

import jakarta.validation.constraints.NotNull;

public record AssignRobotRequest(
        @NotNull Long robotId
) {
}
