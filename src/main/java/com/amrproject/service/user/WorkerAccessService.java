package com.amrproject.service.user;

import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.user.User;
import com.amrproject.exception.StationAccessDeniedException;
import com.amrproject.repository.user.UserStationAssignmentRepository;
import com.amrproject.service.site.StationService;
import com.amrproject.service.task.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 26단계: 작업자 API의 작업장 단위 접근 제어. 12단계까지는 role(WORKER/ADMIN) 검사만 있어
 * 작업자가 배정받지 않은 작업장의 작업도 조회·조작할 수 있었다. 권한 기준은
 * {@code user_station_assignments}이며, ADMIN은 모든 작업장에 접근할 수 있다.
 */
@Service
@Transactional(readOnly = true)
public class WorkerAccessService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserService userService;
    private final StationService stationService;
    private final TaskService taskService;
    private final UserStationAssignmentRepository userStationAssignmentRepository;

    public WorkerAccessService(
            UserService userService,
            StationService stationService,
            TaskService taskService,
            UserStationAssignmentRepository userStationAssignmentRepository
    ) {
        this.userService = userService;
        this.stationService = stationService;
        this.taskService = taskService;
        this.userStationAssignmentRepository = userStationAssignmentRepository;
    }

    public void checkStationAccess(String username, Long stationId) {
        stationService.getById(stationId);
        User user = userService.getByUsername(username);
        if (isAdmin(user) || userStationAssignmentRepository.existsByUserIdAndStationId(user.getId(), stationId)) {
            return;
        }
        throw new StationAccessDeniedException("이 작업장에 대한 권한이 없습니다: " + stationId);
    }

    /** 목적지 선택용 site 작업장 목록 — 그 site 안에 배정된 작업장이 하나라도 있어야 한다. */
    public void checkSiteAccess(String username, Long siteId) {
        User user = userService.getByUsername(username);
        if (isAdmin(user) || userStationAssignmentRepository.existsByUserIdAndStationSiteId(user.getId(), siteId)) {
            return;
        }
        throw new StationAccessDeniedException("이 물류센터에 대한 권한이 없습니다: " + siteId);
    }

    /** 작업은 출발지·도착지 중 한쪽 작업장에만 권한이 있어도 접근할 수 있다(양쪽 작업자가 모두 버튼을 누른다). */
    public void checkTaskAccess(String username, Long taskId) {
        Task task = taskService.getById(taskId);
        User user = userService.getByUsername(username);
        if (isAdmin(user)
                || userStationAssignmentRepository.existsByUserIdAndStationId(user.getId(), task.getOriginStation().getId())
                || userStationAssignmentRepository.existsByUserIdAndStationId(user.getId(), task.getDestinationStation().getId())) {
            return;
        }
        throw new StationAccessDeniedException("이 작업에 대한 권한이 없습니다: " + taskId);
    }

    private boolean isAdmin(User user) {
        return ADMIN_ROLE.equals(user.getRole().getCode());
    }
}
