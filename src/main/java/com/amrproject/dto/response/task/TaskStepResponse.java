package com.amrproject.dto.response.task;

import com.amrproject.domain.entity.task.TaskStep;
import com.amrproject.domain.entity.task.TaskStepStatus;
import com.amrproject.domain.entity.task.TaskStepType;

import java.time.Instant;

public record TaskStepResponse(
        Long id,
        Integer stepNo,
        TaskStepType stepType,
        TaskStepStatus status,
        Instant startedAt,
        Instant completedAt
) {

    public static TaskStepResponse from(TaskStep step) {
        return new TaskStepResponse(
                step.getId(),
                step.getStepNo(),
                step.getStepType(),
                step.getStatus(),
                step.getStartedAt(),
                step.getCompletedAt()
        );
    }
}
