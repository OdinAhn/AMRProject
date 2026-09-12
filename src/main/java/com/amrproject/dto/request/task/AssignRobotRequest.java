package com.amrproject.dto.request.task;

import jakarta.validation.constraints.NotNull;

public record AssignRobotRequest(
        @NotNull Long robotId
) {
}
