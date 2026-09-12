package com.amrproject.service;

import com.amrproject.domain.entity.ConnectionState;
import com.amrproject.domain.entity.LocalizationState;
import com.amrproject.domain.entity.OperationMode;
import com.amrproject.domain.entity.Robot;
import com.amrproject.domain.entity.RobotConfiguration;
import com.amrproject.domain.entity.RobotCurrentState;
import com.amrproject.domain.entity.RobotLifecycleStatus;
import com.amrproject.domain.entity.Station;
import com.amrproject.domain.entity.StationType;
import com.amrproject.domain.entity.Task;
import com.amrproject.domain.entity.TaskAssignmentStatus;
import com.amrproject.domain.entity.TaskStatus;
import com.amrproject.dto.response.DispatchResultResponse;
import com.amrproject.exception.InvalidStateTransitionException;
import com.amrproject.repository.RobotCurrentStateRepository;
import com.amrproject.repository.RobotRepository;
import com.amrproject.repository.StationRepository;
import com.amrproject.repository.TaskAssignmentRepository;
import com.amrproject.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 자동 배정·자동 충전 트리거를 한 번 실행하는 "디스패치 사이클" (7단계). 스케줄러가
 * 없어 관리자가 명시적으로 호출한다 — {@link com.amrproject.controller.DispatchController}.
 * 각 배정/충전 시도는 {@link TaskService}의 별도 트랜잭션으로 커밋되므로, 중간에
 * 하나가 자원 경합(409)으로 실패해도 이전 성공분은 그대로 유지되고 이 메서드
 * 자체는 트랜잭션을 열지 않는다 (그래야 각 시도의 최신 커밋 상태를 다음 시도가 볼 수 있다).
 */
@Service
public class DispatchService {

    private static final int DEFAULT_LOW_BATTERY_PCT = 15;

    private final TaskRepository taskRepository;
    private final RobotRepository robotRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final RobotCurrentStateRepository robotCurrentStateRepository;
    private final StationRepository stationRepository;
    private final SiteService siteService;
    private final TaskService taskService;

    public DispatchService(
            TaskRepository taskRepository,
            RobotRepository robotRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            RobotCurrentStateRepository robotCurrentStateRepository,
            StationRepository stationRepository,
            SiteService siteService,
            TaskService taskService
    ) {
        this.taskRepository = taskRepository;
        this.robotRepository = robotRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.stationRepository = stationRepository;
        this.siteService = siteService;
        this.taskService = taskService;
    }

    public DispatchResultResponse runCycle(Long siteId) {
        siteService.getById(siteId);
        List<Robot> robots = robotRepository.findBySiteId(siteId);
        Set<Long> assignedThisCycle = new HashSet<>();

        List<Long> assignedTaskIds = new ArrayList<>();
        List<Task> queuedTasks = taskRepository.findBySiteIdAndStatusOrderByPriorityDescCreatedAtAsc(siteId, TaskStatus.QUEUED);
        for (Task task : queuedTasks) {
            for (Robot robot : robots) {
                if (assignedThisCycle.contains(robot.getId()) || !isEligibleForAssignment(robot)) {
                    continue;
                }
                try {
                    taskService.assignAndStart(task.getId(), robot.getId());
                    assignedTaskIds.add(task.getId());
                    assignedThisCycle.add(robot.getId());
                    break;
                } catch (InvalidStateTransitionException ex) {
                    // 이미 배정됐거나 목적지 station이 가득 찼다 — 다음 로봇으로 재시도.
                }
            }
        }

        List<Long> chargeTaskIds = new ArrayList<>();
        for (Robot robot : robots) {
            if (assignedThisCycle.contains(robot.getId()) || !needsCharging(robot)) {
                continue;
            }
            Optional<Station> chargingStation = stationRepository.findBySiteIdAndStationType(siteId, StationType.CHARGING).stream().findFirst();
            if (chargingStation.isEmpty()) {
                continue;
            }
            try {
                Task chargeTask = taskService.createChargeTask(robot, chargingStation.get());
                chargeTaskIds.add(chargeTask.getId());
            } catch (InvalidStateTransitionException ex) {
                // 충전소가 가득 찼다 — 다음 사이클에 재시도.
            }
        }

        return new DispatchResultResponse(assignedTaskIds, chargeTaskIds);
    }

    private boolean isEligibleForAssignment(Robot robot) {
        if (robot.getLifecycleStatus() != RobotLifecycleStatus.IN_SERVICE || hasActiveAssignment(robot)) {
            return false;
        }
        Optional<RobotCurrentState> state = robotCurrentStateRepository.findById(robot.getId());
        if (state.isEmpty()) {
            return false;
        }
        RobotCurrentState current = state.get();
        return current.getOperationMode() == OperationMode.AUTO
                && current.getConnectionState() == ConnectionState.ONLINE
                && current.getLocalizationState() == LocalizationState.LOCALIZED
                && current.getBatteryPct() > lowBatteryThreshold(robot);
    }

    private boolean needsCharging(Robot robot) {
        if (robot.getLifecycleStatus() != RobotLifecycleStatus.IN_SERVICE || hasActiveAssignment(robot)) {
            return false;
        }
        return robotCurrentStateRepository.findById(robot.getId())
                .map(state -> state.getBatteryPct() <= lowBatteryThreshold(robot))
                .orElse(false);
    }

    private boolean hasActiveAssignment(Robot robot) {
        return taskAssignmentRepository.findByRobotIdAndStatus(robot.getId(), TaskAssignmentStatus.ACTIVE).isPresent();
    }

    private int lowBatteryThreshold(Robot robot) {
        RobotConfiguration configuration = robot.getAppliedConfiguration();
        return configuration != null ? configuration.getChargingLowThresholdPct() : DEFAULT_LOW_BATTERY_PCT;
    }
}
