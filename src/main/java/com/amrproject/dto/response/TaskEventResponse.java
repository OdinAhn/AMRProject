package com.amrproject.dto.response;

import com.amrproject.domain.entity.TaskEvent;
import com.amrproject.domain.entity.TaskStatus;

import java.time.Instant;

public record TaskEventResponse(
        Long id,
        String eventType,
        TaskStatus fromStatus,
        TaskStatus toStatus,
        Instant createdAt
) {

    public static TaskEventResponse from(TaskEvent event) {
        return new TaskEventResponse(
                event.getId(),
                event.getEventType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getCreatedAt()
        );
    }
}
