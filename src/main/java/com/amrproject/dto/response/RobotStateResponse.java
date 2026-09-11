package com.amrproject.dto.response;

import com.amrproject.domain.entity.ConnectionState;
import com.amrproject.domain.entity.LoadState;
import com.amrproject.domain.entity.LocalizationState;
import com.amrproject.domain.entity.MotionState;
import com.amrproject.domain.entity.OperationMode;
import com.amrproject.domain.entity.RobotCurrentState;
import com.amrproject.domain.entity.RobotStateSnapshot;
import com.amrproject.domain.entity.SafetyState;

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
        Instant receivedAt
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
                state.getReceivedAt()
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
                snapshot.getReceivedAt()
        );
    }
}
