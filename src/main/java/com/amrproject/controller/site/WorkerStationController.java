package com.amrproject.controller.site;

import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.task.TaskStep;
import com.amrproject.domain.entity.user.User;
import com.amrproject.dto.response.site.StationResponse;
import com.amrproject.dto.response.task.TaskResponse;
import com.amrproject.service.site.StationService;
import com.amrproject.service.task.TaskService;
import com.amrproject.service.user.UserService;
import com.amrproject.service.user.WorkerAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 작업자용 작업장 조회 API (docs/API.md §4). 26단계부터 작업장 단위 접근 제어
 * ({@link WorkerAccessService})를 적용한다 — 배정받지 않은 작업장/site는 403.
 */
@RestController
public class WorkerStationController {

    private final UserService userService;
    private final StationService stationService;
    private final TaskService taskService;
    private final WorkerAccessService workerAccessService;

    public WorkerStationController(
            UserService userService,
            StationService stationService,
            TaskService taskService,
            WorkerAccessService workerAccessService
    ) {
        this.userService = userService;
        this.stationService = stationService;
        this.taskService = taskService;
        this.workerAccessService = workerAccessService;
    }

    @GetMapping("/api/worker/stations")
    public List<StationResponse> getMyStations(Authentication authentication) {
        User user = userService.getByUsername(authentication.getName());
        return userService.getStationAssignments(user.getId()).stream()
                .map(assignment -> StationResponse.from(assignment.getStation()))
                .toList();
    }

    @GetMapping("/api/worker/stations/{stationId}/tasks")
    public List<TaskResponse> getStationTasks(@PathVariable Long stationId, Authentication authentication) {
        workerAccessService.checkStationAccess(authentication.getName(), stationId);
        List<Task> tasks = taskService.getActiveByStation(stationId);
        Map<Long, List<TaskStep>> steps = taskService.getStepsByTaskIds(tasks.stream().map(Task::getId).toList());
        return tasks.stream()
                .map(task -> TaskResponse.from(task, steps.getOrDefault(task.getId(), List.of())))
                .toList();
    }

    /**
     * 로봇 호출 시 목적지 station을 고르기 위한 조회 — 작업자는 자기 배정
     * station만 볼 수 있어 같은 site의 다른 station을 볼 방법이 필요하다.
     * 그 site 안에 배정된 작업장이 하나라도 있어야 조회할 수 있다(26단계).
     */
    @GetMapping("/api/worker/sites/{siteId}/stations")
    public List<StationResponse> getSiteStations(@PathVariable Long siteId, Authentication authentication) {
        workerAccessService.checkSiteAccess(authentication.getName(), siteId);
        return stationService.getBySite(siteId).stream().map(StationResponse::from).toList();
    }
}
