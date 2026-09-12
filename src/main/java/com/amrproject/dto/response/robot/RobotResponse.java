package com.amrproject.dto.response.robot;

import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;

import java.time.Instant;

public record RobotResponse(
        Long id,
        String code,
        String name,
        String model,
        String manufacturer,
        Long siteId,
        RobotLifecycleStatus lifecycleStatus,
        Integer desiredConfigVersion,
        Integer appliedConfigVersion,
        Instant createdAt,
        Instant updatedAt
) {

    public static RobotResponse from(Robot robot) {
        return new RobotResponse(
                robot.getId(),
                robot.getCode(),
                robot.getName(),
                robot.getModel(),
                robot.getManufacturer(),
                robot.getSite().getId(),
                robot.getLifecycleStatus(),
                robot.getDesiredConfiguration() != null ? robot.getDesiredConfiguration().getVersionNo() : null,
                robot.getAppliedConfiguration() != null ? robot.getAppliedConfiguration().getVersionNo() : null,
                robot.getCreatedAt(),
                robot.getUpdatedAt()
        );
    }
}
