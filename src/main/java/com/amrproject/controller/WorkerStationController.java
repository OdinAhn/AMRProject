package com.amrproject.controller;

import com.amrproject.domain.entity.user.User;
import com.amrproject.dto.response.site.StationResponse;
import com.amrproject.dto.response.task.TaskResponse;
import com.amrproject.service.StationService;
import com.amrproject.service.TaskService;
import com.amrproject.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 작업자용 작업장 조회 API (docs/API.md §4). station 단위 접근 제어(내가 배정
 * 안 된 station의 작업 조회를 막는 것)는 아직 없다 — role 기반 검사만 있다(10단계).
 */
@RestController
public class WorkerStationController {

    private final UserService userService;
    private final StationService stationService;
    private final TaskService taskService;

    public WorkerStationController(UserService userService, StationService stationService, TaskService taskService) {
        this.userService = userService;
        this.stationService = stationService;
        this.taskService = taskService;
    }

    @GetMapping("/api/worker/stations")
    public List<StationResponse> getMyStations(Authentication authentication) {
        User user = userService.getByUsername(authentication.getName());
        return userService.getStationAssignments(user.getId()).stream()
                .map(assignment -> StationResponse.from(assignment.getStation()))
                .toList();
    }

    @GetMapping("/api/worker/stations/{stationId}/tasks")
    public List<TaskResponse> getStationTasks(@PathVariable Long stationId) {
        return taskService.getActiveByStation(stationId).stream()
                .map(task -> TaskResponse.from(task, taskService.getSteps(task.getId())))
                .toList();
    }

    /**
     * 로봇 호출 시 목적지 station을 고르기 위한 조회 — 작업자는 자기 배정
     * station만 볼 수 있어 같은 site의 다른 station을 볼 방법이 필요하다.
     */
    @GetMapping("/api/worker/sites/{siteId}/stations")
    public List<StationResponse> getSiteStations(@PathVariable Long siteId) {
        return stationService.getBySite(siteId).stream().map(StationResponse::from).toList();
    }
}
