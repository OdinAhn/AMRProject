package com.amrproject.dto.response.task;

import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.task.TaskStatus;
import com.amrproject.domain.entity.task.TaskStep;
import com.amrproject.domain.entity.task.TaskStepStatus;
import com.amrproject.domain.entity.task.TaskStepType;
import com.amrproject.domain.entity.task.TaskType;

import java.time.Instant;
import java.util.ArrayList;
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
        List<String> allowedActions = new ArrayList<>();
        if (CANCELABLE.contains(task.getStatus())) {
            allowedActions.add("CANCEL");
        }
        steps.stream()
                .filter(step -> step.getStatus() == TaskStepStatus.RUNNING)
                .findFirst()
                .ifPresent(step -> {
                    if (step.getStepType() == TaskStepType.CONFIRM_LOAD) {
                        allowedActions.add("DISPATCH");
                    } else if (step.getStepType() == TaskStepType.CONFIRM_UNLOAD) {
                        allowedActions.add("CONFIRM_UNLOAD");
                    }
                });
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
