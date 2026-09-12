package com.amrproject.dto.request.robot;

import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateRobotLifecycleStatusRequest(
        @NotNull RobotLifecycleStatus lifecycleStatus
) {
}
