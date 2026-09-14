package com.amrproject.service.task;

import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.LocalizationState;
import com.amrproject.domain.entity.robot.OperationMode;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotConfiguration;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.domain.entity.robot.SafetyState;
import com.amrproject.domain.entity.site.Station;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.task.TaskAssignmentStatus;
import com.amrproject.domain.entity.task.TaskStatus;
import com.amrproject.dto.response.task.DispatchResultResponse;
import com.amrproject.exception.InvalidStateTransitionException;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.repository.site.StationRepository;
import com.amrproject.repository.task.TaskAssignmentRepository;
import com.amrproject.repository.task.TaskRepository;
import com.amrproject.service.map.MapRouteService;
import com.amrproject.service.map.MapRouteService.RouteGraph;
import com.amrproject.service.site.SiteService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

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
    private final MapRouteService mapRouteService;

    public DispatchService(
            TaskRepository taskRepository,
            RobotRepository robotRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            RobotCurrentStateRepository robotCurrentStateRepository,
            StationRepository stationRepository,
            SiteService siteService,
            TaskService taskService,
            MapRouteService mapRouteService
    ) {
        this.taskRepository = taskRepository;
        this.robotRepository = robotRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.stationRepository = stationRepository;
        this.siteService = siteService;
        this.taskService = taskService;
        this.mapRouteService = mapRouteService;
    }

    /**
     * 38단계: 대기 작업을 우선순위 순으로 하나씩, 그 작업의 출발 작업장까지 <b>통로 거리가 가장 가까운</b> 배정 가능한 로봇에 맡긴다.
     * 작업 단위로 욕심껏(greedy) 고르므로 사이클 전체의 이동 거리 합이 최소라는 보장은 없다 — 우선순위 높은 작업이 가까운 로봇을 먼저 가져간다.
     * 활성 지도가 없거나 출발 작업장이 지도에 연결되지 않았으면 예전처럼 로봇 id 순서로 고른다.
     */
    public DispatchResultResponse runCycle(Long siteId) {
        siteService.getById(siteId);
        List<Robot> robots = robotRepository.findBySiteId(siteId).stream()
                .sorted(Comparator.comparing(Robot::getId))
                .toList();
        Set<Long> assignedThisCycle = new HashSet<>();
        Optional<Long> activeVersionId = mapRouteService.findActiveVersionId(siteId);
        // 지도는 사이클마다 한 번만 읽는다(작업마다 다시 읽지 않음)
        RouteGraph graph = activeVersionId.map(mapRouteService::loadGraph).orElse(null);

        List<Long> assignedTaskIds = new ArrayList<>();
        List<DispatchResultResponse.Assignment> assignments = new ArrayList<>();
        List<Task> queuedTasks = taskRepository.findBySiteIdAndStatusOrderByPriorityDescCreatedAtAsc(siteId, TaskStatus.QUEUED);
        for (Task task : queuedTasks) {
            List<Candidate> candidates = rankCandidates(task, robots, assignedThisCycle, activeVersionId.orElse(null), graph);
            for (Candidate candidate : candidates) {
                try {
                    taskService.assignAndStart(task.getId(), candidate.robot().getId());
                    assignedTaskIds.add(task.getId());
                    assignments.add(new DispatchResultResponse.Assignment(
                            task.getId(), candidate.robot().getId(), candidate.robot().getCode(), candidate.roundedDistance()
                    ));
                    assignedThisCycle.add(candidate.robot().getId());
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

        return new DispatchResultResponse(assignedTaskIds, chargeTaskIds, assignments);
    }

    /**
     * 배정 가능한 로봇을 출발 작업장까지의 통로 거리순으로 정렬한다. 위치(현재 노드)를 모르거나 활성 지도에서 출발지로 갈 길이 없는 로봇은
     * 거리를 아는 로봇 뒤에 둔다. 거리가 같으면 로봇 id 순서다(안정 정렬이라 입력 순서 유지).
     */
    private List<Candidate> rankCandidates(Task task, List<Robot> robots, Set<Long> assignedThisCycle, Long activeVersionId, RouteGraph graph) {
        Map<Long, Double> distancesToOrigin = graph == null ? Map.of()
                : mapRouteService.findStationNodeId(task.getOriginStation().getId(), activeVersionId)
                        .map(graph::distancesTo)
                        .orElse(Map.of());
        List<Candidate> candidates = new ArrayList<>();
        for (Robot robot : robots) {
            if (assignedThisCycle.contains(robot.getId())) {
                continue;
            }
            eligibleState(robot).ifPresent(state -> candidates.add(new Candidate(
                    robot,
                    state.getCurrentNodeId() == null ? null : distancesToOrigin.get(state.getCurrentNodeId())
            )));
        }
        candidates.sort(Comparator.comparing(Candidate::distance, Comparator.nullsLast(Comparator.naturalOrder())));
        return candidates;
    }

    /** distance: 출발 작업장까지 통로 거리(지도 좌표 단위), 모르면 null */
    private record Candidate(Robot robot, Double distance) {
        Double roundedDistance() {
            return distance == null ? null : Math.round(distance * 10) / 10.0;
        }
    }

    /** 배정 가능하면 그 로봇의 현재 상태(위치 포함)를, 아니면 빈 값을 준다. */
    private Optional<RobotCurrentState> eligibleState(Robot robot) {
        if (robot.getLifecycleStatus() != RobotLifecycleStatus.IN_SERVICE || hasActiveAssignment(robot)) {
            return Optional.empty();
        }
        return robotCurrentStateRepository.findById(robot.getId()).filter(current -> current.getOperationMode() == OperationMode.AUTO
                && current.getSafetyState() == SafetyState.NORMAL // 33단계: 비상·보호 정지 중인 로봇은 배정하지 않는다
                && current.getConnectionState() == ConnectionState.ONLINE
                && current.getLocalizationState() == LocalizationState.LOCALIZED
                && current.getBatteryPct() > lowBatteryThreshold(robot));
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
