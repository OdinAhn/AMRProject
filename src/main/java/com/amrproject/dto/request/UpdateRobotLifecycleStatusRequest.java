package com.amrproject.dto.request;

import com.amrproject.domain.entity.RobotLifecycleStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateRobotLifecycleStatusRequest(
        @NotNull RobotLifecycleStatus lifecycleStatus
) {
}
