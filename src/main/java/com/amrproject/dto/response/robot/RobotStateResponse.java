package com.amrproject.dto.response.robot;

import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.LoadState;
import com.amrproject.domain.entity.robot.LocalizationState;
import com.amrproject.domain.entity.robot.MotionState;
import com.amrproject.domain.entity.robot.OperationMode;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.robot.RobotStateSnapshot;
import com.amrproject.domain.entity.robot.SafetyState;

import java.time.Instant;

public record RobotStateResponse(
        Long robotId,
        ConnectionState connectionState,
        OperationMode operationMode,
        MotionState motionState,
        SafetyState safetyState,
        LoadState loadState,
        LocalizationState localizationState,
        Integer batteryPct,
        Integer stateVersion,
        Instant reportedAt,
        Instant receivedAt,
        /** 30단계: 마지막으로 도착한 지도 노드 id(모르면 null) */
        Long currentNodeId
) {

    public static RobotStateResponse from(RobotCurrentState state) {
        return new RobotStateResponse(
                state.getRobotId(),
                state.getConnectionState(),
                state.getOperationMode(),
                state.getMotionState(),
                state.getSafetyState(),
                state.getLoadState(),
                state.getLocalizationState(),
                state.getBatteryPct(),
                state.getStateVersion(),
                state.getReportedAt(),
                state.getReceivedAt(),
                state.getCurrentNodeId()
        );
    }

    public static RobotStateResponse from(RobotStateSnapshot snapshot) {
        return new RobotStateResponse(
                snapshot.getRobot().getId(),
                snapshot.getConnectionState(),
                snapshot.getOperationMode(),
                snapshot.getMotionState(),
                snapshot.getSafetyState(),
                snapshot.getLoadState(),
                snapshot.getLocalizationState(),
                snapshot.getBatteryPct(),
                null,
                snapshot.getReportedAt(),
                snapshot.getReceivedAt(),
                snapshot.getCurrentNodeId()
        );
    }
}
