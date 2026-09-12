package com.amrproject.dto.response;

import com.amrproject.domain.entity.Alarm;
import com.amrproject.domain.entity.AlarmSeverity;
import com.amrproject.domain.entity.AlarmSourceType;
import com.amrproject.domain.entity.AlarmStatus;
import com.amrproject.domain.entity.AlarmType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record AlarmResponse(
        Long id,
        AlarmType alarmType,
        AlarmSeverity severity,
        AlarmSourceType sourceType,
        Long sourceId,
        AlarmStatus status,
        String message,
        Instant acknowledgedAt,
        Long acknowledgedBy,
        Instant resolvedAt,
        Long resolvedBy,
        List<String> allowedActions,
        Instant createdAt,
        Instant updatedAt
) {

    public static AlarmResponse from(Alarm alarm) {
        List<String> allowedActions = new ArrayList<>();
        if (alarm.getStatus() == AlarmStatus.OPEN) {
            allowedActions.add("ACKNOWLEDGE");
            allowedActions.add("RESOLVE");
        } else if (alarm.getStatus() == AlarmStatus.ACKNOWLEDGED) {
            allowedActions.add("RESOLVE");
        }
        return new AlarmResponse(
                alarm.getId(),
                alarm.getAlarmType(),
                alarm.getSeverity(),
                alarm.getSourceType(),
                alarm.getSourceId(),
                alarm.getStatus(),
                alarm.getMessage(),
                alarm.getAcknowledgedAt(),
                alarm.getAcknowledgedBy() != null ? alarm.getAcknowledgedBy().getId() : null,
                alarm.getResolvedAt(),
                alarm.getResolvedBy() != null ? alarm.getResolvedBy().getId() : null,
                allowedActions,
                alarm.getCreatedAt(),
                alarm.getUpdatedAt()
        );
    }
}
