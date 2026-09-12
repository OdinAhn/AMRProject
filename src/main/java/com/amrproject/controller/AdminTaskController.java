package com.amrproject.controller;

import com.amrproject.dto.request.AssignRobotRequest;
import com.amrproject.dto.request.RecoverTaskRequest;
import com.amrproject.dto.response.TaskEventResponse;
import com.amrproject.dto.response.TaskResponse;
import com.amrproject.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tasks")
public class AdminTaskController {

    private final TaskService taskService;

    public AdminTaskController(TaskService taskService) {
        this.taskService = taskService;
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
    public TaskResponse cancel(@PathVariable Long taskId) {
        taskService.requestCancel(taskId, null);
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
