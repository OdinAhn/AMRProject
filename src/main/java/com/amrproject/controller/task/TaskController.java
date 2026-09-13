package com.amrproject.controller.task;

import com.amrproject.domain.entity.user.User;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.dto.request.task.ReportUnavailableRequest;
import com.amrproject.dto.response.task.TaskResponse;
import com.amrproject.service.task.TaskCreationResult;
import com.amrproject.service.task.TaskService;
import com.amrproject.service.user.UserService;
import com.amrproject.service.user.WorkerAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 작업자용 작업 API (docs/API.md §4). 26단계부터 모든 요청이 작업장 단위 권한
 * ({@link WorkerAccessService})을 먼저 검사한다.
 */
@RestController
@RequestMapping("/api/worker/tasks")
public class TaskController {

    private final TaskService taskService;
    private final UserService userService;
    private final WorkerAccessService workerAccessService;

    public TaskController(TaskService taskService, UserService userService, WorkerAccessService workerAccessService) {
        this.taskService = taskService;
        this.userService = userService;
        this.workerAccessService = workerAccessService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTaskRequest request,
            Authentication authentication
    ) {
        workerAccessService.checkStationAccess(authentication.getName(), request.originStationId());
        User requestedBy = userService.getByUsername(authentication.getName());
        TaskCreationResult result = taskService.createTask(request, idempotencyKey, requestedBy);
        TaskResponse response = TaskResponse.from(result.task(), taskService.getSteps(result.task().getId()));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{taskId}")
    public TaskResponse getById(@PathVariable Long taskId, Authentication authentication) {
        workerAccessService.checkTaskAccess(authentication.getName(), taskId);
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    @PostMapping("/{taskId}/dispatch")
    public TaskResponse dispatch(@PathVariable Long taskId, Authentication authentication) {
        workerAccessService.checkTaskAccess(authentication.getName(), taskId);
        taskService.dispatchAfterLoad(taskId);
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    @PostMapping("/{taskId}/confirm-unload")
    public TaskResponse confirmUnload(@PathVariable Long taskId, Authentication authentication) {
        workerAccessService.checkTaskAccess(authentication.getName(), taskId);
        taskService.confirmUnload(taskId);
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    @PostMapping("/{taskId}/report-unavailable")
    public TaskResponse reportUnavailable(
            @PathVariable Long taskId,
            @Valid @RequestBody ReportUnavailableRequest request,
            Authentication authentication
    ) {
        workerAccessService.checkTaskAccess(authentication.getName(), taskId);
        taskService.reportUnavailable(taskId, request.reason());
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    @PostMapping("/{taskId}/cancel")
    public TaskResponse cancel(@PathVariable Long taskId, Authentication authentication) {
        workerAccessService.checkTaskAccess(authentication.getName(), taskId);
        User actor = userService.getByUsername(authentication.getName());
        taskService.requestCancel(taskId, actor);
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }
}
