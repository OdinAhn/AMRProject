package com.amrproject.controller.task;

import com.amrproject.controller.PageRequests;
import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.task.TaskStatus;
import com.amrproject.domain.entity.task.TaskStep;
import com.amrproject.dto.response.PageResponse;
import com.amrproject.domain.entity.user.User;
import com.amrproject.dto.request.task.AssignRobotRequest;
import com.amrproject.dto.request.task.ChangeTaskPriorityRequest;
import com.amrproject.dto.request.task.RecoverTaskRequest;
import com.amrproject.dto.response.task.TaskEventResponse;
import com.amrproject.dto.response.task.TaskResponse;
import com.amrproject.service.task.TaskService;
import com.amrproject.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

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
     * 31단계: 페이지 응답(최신순)으로 바뀌었고, 단계는 페이지의 작업들을 한 번에 읽는다.
     */
    @GetMapping
    public PageResponse<TaskResponse> getAll(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_SIZE) int size
    ) {
        Page<Task> tasks = taskService.getPage(siteId, status, PageRequests.newestFirst(page, size));
        Map<Long, List<TaskStep>> steps = taskService.getStepsByTaskIds(tasks.map(Task::getId).getContent());
        return PageResponse.of(tasks, task -> TaskResponse.from(task, steps.getOrDefault(task.getId(), List.of())));
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

    /** 33단계: 작업 흐름 일시정지(RUNNING → PAUSED). 로봇을 물리적으로 멈추려면 로봇 비상정지를 쓴다. */
    @PostMapping("/{taskId}/pause")
    public TaskResponse pause(@PathVariable Long taskId, Authentication authentication) {
        taskService.pause(taskId, userService.getByUsername(authentication.getName()));
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    /** 33단계: 일시정지 해제(PAUSED → RUNNING). */
    @PostMapping("/{taskId}/resume")
    public TaskResponse resume(@PathVariable Long taskId, Authentication authentication) {
        taskService.resume(taskId, userService.getByUsername(authentication.getName()));
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    /** 33단계: 대기(QUEUED) 작업 우선순위 변경. */
    @PatchMapping("/{taskId}/priority")
    public TaskResponse changePriority(
            @PathVariable Long taskId,
            @Valid @RequestBody ChangeTaskPriorityRequest request,
            Authentication authentication
    ) {
        taskService.changePriority(taskId, request.priority(), userService.getByUsername(authentication.getName()));
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }

    @PostMapping("/{taskId}/recover")
    public TaskResponse recover(@PathVariable Long taskId, @Valid @RequestBody RecoverTaskRequest request) {
        taskService.recover(taskId, request.resolution());
        return TaskResponse.from(taskService.getById(taskId), taskService.getSteps(taskId));
    }
}
