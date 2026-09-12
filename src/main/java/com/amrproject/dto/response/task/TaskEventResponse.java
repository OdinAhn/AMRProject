package com.amrproject.dto.response.task;

import com.amrproject.domain.entity.task.TaskEvent;
import com.amrproject.domain.entity.task.TaskStatus;

import java.time.Instant;

public record TaskEventResponse(
        Long id,
        String eventType,
        TaskStatus fromStatus,
        TaskStatus toStatus,
        String payload,
        Instant createdAt
) {

    public static TaskEventResponse from(TaskEvent event) {
        return new TaskEventResponse(
                event.getId(),
                event.getEventType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getPayload(),
                event.getCreatedAt()
        );
    }
}
