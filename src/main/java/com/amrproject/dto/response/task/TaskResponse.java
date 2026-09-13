package com.amrproject.dto.response.task;

import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.task.TaskAssignment;
import com.amrproject.domain.entity.task.TaskStatus;
import com.amrproject.domain.entity.task.TaskStep;
import com.amrproject.domain.entity.task.TaskStepStatus;
import com.amrproject.domain.entity.task.TaskStepType;
import com.amrproject.domain.entity.task.TaskType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 28단계: 화면이 id만으로는 작업을 식별하기 어려워 출발지/도착지 작업장 이름과 배정 로봇(id/code)을
 * 추가했다(필드 추가만 — 하위 호환). allowedActions에 REPORT_UNAVAILABLE도 추가했다.
 */
public record TaskResponse(
        Long id,
        String taskNo,
        Long siteId,
        TaskType taskType,
        Integer priority,
        TaskStatus status,
        Long originStationId,
        String originStationName,
        Long destinationStationId,
        String destinationStationName,
        Long currentAssignmentId,
        Long assignedRobotId,
        String assignedRobotCode,
        List<TaskStepResponse> steps,
        List<String> allowedActions,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {

    private static final Set<TaskStatus> CANCELABLE = Set.of(
            TaskStatus.QUEUED, TaskStatus.ASSIGNED, TaskStatus.RUNNING, TaskStatus.PAUSED
    );

    /** TaskService.reportUnavailable이 허용하는 상태와 같다. */
    private static final Set<TaskStatus> UNAVAILABLE_REPORTABLE = CANCELABLE;

    public static TaskResponse from(Task task, List<TaskStep> steps) {
        List<String> allowedActions = new ArrayList<>();
        if (CANCELABLE.contains(task.getStatus())) {
            allowedActions.add("CANCEL");
        }
        // 28단계: 작업이 RUNNING일 때만 단계 버튼을 준다 — 예전엔 단계만 봐서 RECOVERY_REQUIRED/CANCEL_REQUESTED인
        // 작업에도 DISPATCH/CONFIRM_UNLOAD가 나갔고, 누르면 상태 전이 검증에서 409가 났다(브라우저 검증 중 발견).
        steps.stream()
                .filter(step -> task.getStatus() == TaskStatus.RUNNING)
                .filter(step -> step.getStatus() == TaskStepStatus.RUNNING)
                .findFirst()
                .ifPresent(step -> {
                    if (step.getStepType() == TaskStepType.CONFIRM_LOAD) {
                        allowedActions.add("DISPATCH");
                    } else if (step.getStepType() == TaskStepType.CONFIRM_UNLOAD) {
                        allowedActions.add("CONFIRM_UNLOAD");
                    }
                });
        if (UNAVAILABLE_REPORTABLE.contains(task.getStatus())) {
            allowedActions.add("REPORT_UNAVAILABLE");
        }
        // 33단계: 관리자용 동작은 기존 값의 순서를 바꾸지 않도록 뒤에 붙인다.
        if (task.getStatus() == TaskStatus.RUNNING) {
            allowedActions.add("PAUSE");
        }
        if (task.getStatus() == TaskStatus.PAUSED) {
            allowedActions.add("RESUME");
        }
        if (task.getStatus() == TaskStatus.QUEUED) {
            allowedActions.add("CHANGE_PRIORITY");
        }
        TaskAssignment assignment = task.getCurrentAssignment();
        return new TaskResponse(
                task.getId(),
                task.getTaskNo(),
                task.getSite().getId(),
                task.getTaskType(),
                task.getPriority(),
                task.getStatus(),
                task.getOriginStation().getId(),
                task.getOriginStation().getName(),
                task.getDestinationStation().getId(),
                task.getDestinationStation().getName(),
                assignment != null ? assignment.getId() : null,
                assignment != null ? assignment.getRobot().getId() : null,
                assignment != null ? assignment.getRobot().getCode() : null,
                steps.stream().map(TaskStepResponse::from).toList(),
                allowedActions,
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt()
        );
    }
}
