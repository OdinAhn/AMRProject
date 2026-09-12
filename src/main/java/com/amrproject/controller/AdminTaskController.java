package com.amrproject.controller;

import com.amrproject.domain.entity.TaskStatus;
import com.amrproject.domain.entity.User;
import com.amrproject.dto.request.AssignRobotRequest;
import com.amrproject.dto.request.RecoverTaskRequest;
import com.amrproject.dto.response.TaskEventResponse;
import com.amrproject.dto.response.TaskResponse;
import com.amrproject.service.TaskService;
import com.amrproject.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tasks")
public class AdminTaskController {

    private final TaskService taskService;
    private final UserService userService;

    public AdminTaskController(TaskService taskService, UserService userService) {
        this.taskService = taskService;
        this.userService = userService;
    }

    /**
     * 재접속 대조(8단계): station/robot/기간 필터는 아직 미구현 (docs/API.md §3).
     */
    @GetMapping
    public List<TaskResponse> getAll(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) TaskStatus status
    ) {
        return taskService.getAll(siteId, status).stream()
                .map(task -> TaskResponse.from(task, taskService.getSteps(task.getId())))
                .toList();
    }

    @GetMapping("/{taskId}")
    public TaskResponse getById(@PathVariable Long taskId) {
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    @GetMapping("/{taskId}/events")
    public List<TaskEventResponse> getEvents(@PathVariable Long taskId) {
        return taskService.getEvents(taskId).stream().map(TaskEventResponse::from).toList();
    }

    @PostMapping("/{taskId}/cancel")
    public TaskResponse cancel(@PathVariable Long taskId, Authentication authentication) {
        User actor = userService.getByUsername(authentication.getName());
        taskService.requestCancel(taskId, actor);
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    @PostMapping("/{taskId}/assign")
    public TaskResponse assign(@PathVariable Long taskId, @Valid @RequestBody AssignRobotRequest request) {
        taskService.assignAndStart(taskId, request.robotId());
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    @PostMapping("/{taskId}/recover")
    public TaskResponse recover(@PathVariable Long taskId, @Valid @RequestBody RecoverTaskRequest request) {
        taskService.recover(taskId, request.resolution());
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }
}
