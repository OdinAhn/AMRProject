package com.amrproject.dto.response.robot;

import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCurrentState;
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
        Instant updatedAt,
        /** 30단계: 목록에서 바로 보이도록 현재 연결 상태·배터리를 함께 준다(상태 보고가 없으면 null) */
        ConnectionState connectionState,
        Integer batteryPct
) {

    public static RobotResponse from(Robot robot) {
        return from(robot, null);
    }

    public static RobotResponse from(Robot robot, RobotCurrentState state) {
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
                robot.getUpdatedAt(),
                state != null ? state.getConnectionState() : null,
                state != null ? state.getBatteryPct() : null
        );
    }
}
