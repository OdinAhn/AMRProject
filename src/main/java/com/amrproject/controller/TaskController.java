package com.amrproject.controller;

import com.amrproject.domain.entity.user.User;
import com.amrproject.dto.request.task.CreateTaskRequest;
import com.amrproject.dto.request.task.ReportUnavailableRequest;
import com.amrproject.dto.response.task.TaskResponse;
import com.amrproject.service.TaskCreationResult;
import com.amrproject.service.TaskService;
import com.amrproject.service.UserService;
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
 * 작업자용 작업 API (docs/API.md §4).
 */
@RestController
@RequestMapping("/api/worker/tasks")
public class TaskController {

    private final TaskService taskService;
    private final UserService userService;

    public TaskController(TaskService taskService, UserService userService) {
        this.taskService = taskService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTaskRequest request,
            Authentication authentication
    ) {
        User requestedBy = userService.getByUsername(authentication.getName());
        TaskCreationResult result = taskService.createTask(request, idempotencyKey, requestedBy);
        TaskResponse response = TaskResponse.from(result.task(), taskService.getSteps(result.task().getId()));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{taskId}")
    public TaskResponse getById(@PathVariable Long taskId) {
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    @PostMapping("/{taskId}/dispatch")
    public TaskResponse dispatch(@PathVariable Long taskId) {
        taskService.dispatchAfterLoad(taskId);
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    @PostMapping("/{taskId}/confirm-unload")
    public TaskResponse confirmUnload(@PathVariable Long taskId) {
        taskService.confirmUnload(taskId);
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    @PostMapping("/{taskId}/report-unavailable")
    public TaskResponse reportUnavailable(@PathVariable Long taskId, @Valid @RequestBody ReportUnavailableRequest request) {
        taskService.reportUnavailable(taskId, request.reason());
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    @PostMapping("/{taskId}/cancel")
    public TaskResponse cancel(@PathVariable Long taskId, Authentication authentication) {
        User actor = userService.getByUsername(authentication.getName());
        taskService.requestCancel(taskId, actor);
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }
}
