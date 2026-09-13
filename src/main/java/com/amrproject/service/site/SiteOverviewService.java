package com.amrproject.service.site;

import com.amrproject.domain.entity.alarm.AlarmStatus;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.task.TaskAssignmentStatus;
import com.amrproject.domain.entity.task.TaskStatus;
import com.amrproject.dto.response.site.SiteOverviewResponse;
import com.amrproject.dto.response.site.SiteOverviewResponse.RobotSummary;
import com.amrproject.repository.alarm.AlarmRepository;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.repository.task.TaskAssignmentRepository;
import com.amrproject.repository.task.TaskRepository;
import com.amrproject.service.map.MapRouteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 30단계: 관제 대시보드용 물류센터 현황 조회. 화면이 로봇·작업·알람 API를 따로 여러 번 부르지 않도록 한 번에 모은다.
 * 로봇 수가 물류센터당 수십 대 수준이라 로봇별 상태·배정 조회를 개별로 해도 충분하다(대용량 최적화는 31단계).
 */
@Service
@Transactional(readOnly = true)
public class SiteOverviewService {

    private static final List<TaskStatus> OPEN_TASK_STATUSES = List.of(
            TaskStatus.QUEUED, TaskStatus.ASSIGNED, TaskStatus.RUNNING, TaskStatus.PAUSED,
            TaskStatus.CANCEL_REQUESTED, TaskStatus.RECOVERY_REQUIRED
    );
    private static final List<AlarmStatus> OPEN_ALARM_STATUSES = List.of(AlarmStatus.OPEN, AlarmStatus.ACKNOWLEDGED);

    private final SiteService siteService;
    private final RobotRepository robotRepository;
    private final RobotCurrentStateRepository robotCurrentStateRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskRepository taskRepository;
    private final AlarmRepository alarmRepository;
    private final MapRouteService mapRouteService;

    public SiteOverviewService(
            SiteService siteService,
            RobotRepository robotRepository,
            RobotCurrentStateRepository robotCurrentStateRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            TaskRepository taskRepository,
            AlarmRepository alarmRepository,
            MapRouteService mapRouteService
    ) {
        this.siteService = siteService;
        this.robotRepository = robotRepository;
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.taskRepository = taskRepository;
        this.alarmRepository = alarmRepository;
        this.mapRouteService = mapRouteService;
    }

    public SiteOverviewResponse getOverview(Long siteId) {
        siteService.getById(siteId);

        List<RobotSummary> robots = robotRepository.findBySiteId(siteId).stream()
                .sorted(Comparator.comparing(Robot::getCode))
                .map(this::summarize)
                .toList();

        Map<TaskStatus, Long> openTaskCounts = new EnumMap<>(TaskStatus.class);
        OPEN_TASK_STATUSES.forEach(status -> openTaskCounts.put(status, 0L));
        taskRepository.countBySiteAndStatusIn(siteId, OPEN_TASK_STATUSES)
                .forEach(count -> openTaskCounts.put(count.getStatus(), count.getCount()));

        return new SiteOverviewResponse(
                siteId,
                mapRouteService.findActiveVersionId(siteId).orElse(null),
                robots,
                openTaskCounts,
                alarmRepository.countBySiteAndStatusIn(siteId, OPEN_ALARM_STATUSES)
        );
    }

    private RobotSummary summarize(Robot robot) {
        RobotCurrentState state = robotCurrentStateRepository.findById(robot.getId()).orElse(null);
        Task currentTask = taskAssignmentRepository.findByRobotIdAndStatus(robot.getId(), TaskAssignmentStatus.ACTIVE)
                .map(assignment -> assignment.getTask())
                .orElse(null);
        return new RobotSummary(
                robot.getId(),
                robot.getCode(),
                robot.getName(),
                robot.getLifecycleStatus(),
                state != null ? state.getConnectionState() : null,
                state != null ? state.getMotionState() : null,
                state != null ? state.getSafetyState() : null,
                state != null ? state.getBatteryPct() : null,
                state != null ? state.getCurrentNodeId() : null,
                currentTask != null ? currentTask.getId() : null,
                currentTask != null ? currentTask.getTaskNo() : null
        );
    }
}
