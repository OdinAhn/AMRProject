package com.amrproject.dto.response;

import com.amrproject.domain.entity.Robot;
import com.amrproject.domain.entity.RobotLifecycleStatus;

import java.time.Instant;

public record RobotResponse(
        Long id,
        String code,
        String name,
        String model,
        String manufacturer,
        Long siteId,
        RobotLifecycleStatus lifecycleStatus,
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
                robot.getCreatedAt(),
                robot.getUpdatedAt()
        );
    }
}
