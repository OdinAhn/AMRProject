package com.amrproject.dto.response;

import com.amrproject.domain.entity.Task;
import com.amrproject.domain.entity.TaskStatus;
import com.amrproject.domain.entity.TaskStep;
import com.amrproject.domain.entity.TaskType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record TaskResponse(
        Long id,
        String taskNo,
        Long siteId,
        TaskType taskType,
        Integer priority,
        TaskStatus status,
        Long originStationId,
        Long destinationStationId,
        Long currentAssignmentId,
        List<TaskStepResponse> steps,
        List<String> allowedActions,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {

    private static final Set<TaskStatus> CANCELABLE = Set.of(
            TaskStatus.QUEUED, TaskStatus.ASSIGNED, TaskStatus.RUNNING, TaskStatus.PAUSED
    );

    public static TaskResponse from(Task task, List<TaskStep> steps) {
        List<String> allowedActions = CANCELABLE.contains(task.getStatus())
                ? List.of("CANCEL")
                : List.of();
        return new TaskResponse(
                task.getId(),
                task.getTaskNo(),
                task.getSite().getId(),
                task.getTaskType(),
                task.getPriority(),
                task.getStatus(),
                task.getOriginStation().getId(),
                task.getDestinationStation().getId(),
                task.getCurrentAssignment() != null ? task.getCurrentAssignment().getId() : null,
                steps.stream().map(TaskStepResponse::from).toList(),
                allowedActions,
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt()
        );
    }
}
