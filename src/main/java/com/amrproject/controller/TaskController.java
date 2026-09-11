package com.amrproject.controller;

import com.amrproject.dto.request.CreateTaskRequest;
import com.amrproject.dto.response.TaskResponse;
import com.amrproject.service.TaskCreationResult;
import com.amrproject.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 작업자용 작업 API (docs/API.md §4). 인증이 아직 없어 requestedBy는 null로 기록한다.
 */
@RestController
@RequestMapping("/api/worker/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        TaskCreationResult result = taskService.createTask(request, idempotencyKey, null);
        TaskResponse response = TaskResponse.from(result.task(), taskService.getSteps(result.task().getId()));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{taskId}")
    public TaskResponse getById(@PathVariable Long taskId) {
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }
}
