package com.amrproject.controller;

import com.amrproject.dto.request.task.CreateExternalTaskRequest;
import com.amrproject.dto.response.task.TaskResponse;
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
 * 외부 시스템(WMS/WCS 등) 접수/조회 API (docs/API.md §6, 9단계). 최소 접수/조회
 * 계약만 구현한다 — 실제 연동 대상(WMS 존재 여부 등)은 DESIGN.md 미확정 항목이다.
 */
@RestController
@RequestMapping("/api/external/tasks")
public class ExternalTaskController {

    private final TaskService taskService;

    public ExternalTaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateExternalTaskRequest request
    ) {
        TaskCreationResult result = taskService.createExternalTask(request, idempotencyKey);
        TaskResponse response = TaskResponse.from(result.task(), taskService.getSteps(result.task().getId()));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{externalRef}")
    public TaskResponse getByExternalRef(@PathVariable String externalRef) {
        var task = taskService.getByExternalRef(externalRef);
        return TaskResponse.from(task, taskService.getSteps(task.getId()));
    }
}
