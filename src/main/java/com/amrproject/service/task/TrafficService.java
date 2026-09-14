package com.amrproject.service.task;

import com.amrproject.domain.entity.map.Node;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.site.Station;
import com.amrproject.domain.entity.task.ResourceReservation;
import com.amrproject.domain.entity.task.ResourceReservationStatus;
import com.amrproject.domain.entity.task.ResourceType;
import com.amrproject.domain.entity.task.TaskStep;
import com.amrproject.repository.map.NodeRepository;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.repository.site.SiteRepository;
import com.amrproject.repository.task.ResourceReservationRepository;
import com.amrproject.service.map.MapRouteService;
import com.amrproject.service.map.MapRouteService.Waypoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 46단계: 교통 관리 — 로봇이 지나갈 지도 노드를 미리 예약해, 두 로봇이 같은 통로·교차점에 동시에 들어가지 않게 한다.
 *
 * <p>이동 명령을 보내기 전에 <b>다른 로봇이 서 있는 노드(현재 노드)와 다른 로봇의 이동 경로로 예약된 노드를 피해</b> 로봇 현재 노드에서
 * 목적지 작업장 노드까지의 최단 경로를 구한다. 그런 경로가 있으면(더 멀어도) 경로 노드를 모두 예약하고 그 경로를 명령에 담는다.
 * 목적지가 막혀 있거나 피해 갈 길이 없으면 기다린다(명령은 보내지 않고 PENDING) — 한 경로만 고집하면 격자 지도에서 멈춰 있는 로봇 하나
 * 때문에 같은 길이의 다른 길이 있어도 끝없이 기다렸다(가상 로봇으로 검증하다 발견). — MQTT 로봇에는 이 경로를 그대로 주문으로 보내(전체 경로 주문)
 * 로봇이 예약한 길로만 가게 한다. 예약은 이동이 성공·실패로 끝나면 풀리고(도착한 로봇은 현재 노드로 계속 막는다), 결과 불명이면
 * 로봇이 경로 어딘가에 있을 수 있어 복구 처리 전까지 유지한다.
 *
 * <p>지도가 없거나, 목적지 작업장이 지도에 연결되지 않았거나, 로봇 위치를 모르거나, 갈 길이 없으면 교통 관리 없이 예전처럼 보낸다.
 *
 * <p>동시성: 경로 계획은 물류센터 행을 잠가 물류센터 단위로 직렬화하고, 다른 로봇의 위치·예약은 잠금 조회(FOR SHARE)로 최신 값을 읽는다.
 * robots 행은 잠그지 않는다 — 배정(TaskService.assignRobot)이 로봇 행을 잠근 채 이 계획을 기다리므로, 잠그면 교착된다.
 * 교착(서로의 자리를 기다리는 두 로봇)은 자동으로 풀지 않고, 오래 기다리면 TRAFFIC_BLOCKED 알람으로 알린다.
 */
@Service
public class TrafficService {

    private final boolean enabled;
    private final long blockedAlarmSeconds;
    private final MapRouteService mapRouteService;
    private final SiteRepository siteRepository;
    private final RobotRepository robotRepository;
    private final RobotCurrentStateRepository robotCurrentStateRepository;
    private final ResourceReservationRepository resourceReservationRepository;
    private final NodeRepository nodeRepository;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher eventPublisher;

    public TrafficService(
            @Value("${app.traffic.enabled:true}") boolean enabled,
            @Value("${app.traffic.blocked-alarm-seconds:60}") long blockedAlarmSeconds,
            MapRouteService mapRouteService,
            SiteRepository siteRepository,
            RobotRepository robotRepository,
            RobotCurrentStateRepository robotCurrentStateRepository,
            ResourceReservationRepository resourceReservationRepository,
            NodeRepository nodeRepository,
            JsonMapper jsonMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.enabled = enabled;
        this.blockedAlarmSeconds = blockedAlarmSeconds;
        this.mapRouteService = mapRouteService;
        this.siteRepository = siteRepository;
        this.robotRepository = robotRepository;
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.resourceReservationRepository = resourceReservationRepository;
        this.nodeRepository = nodeRepository;
        this.jsonMapper = jsonMapper;
        this.eventPublisher = eventPublisher;
    }

    /** 호출한 트랜잭션 안에서 경로를 계획한다. CLEAR면 경로 노드를 이 로봇 이름으로 예약한 뒤다. */
    public Plan plan(Robot robot, Station target, TaskStep step) {
        if (!enabled || target == null) {
            return Plan.UNMANAGED;
        }
        Long siteId = robot.getSite().getId();
        Optional<Long> versionId = mapRouteService.findActiveVersionId(siteId);
        Optional<Long> targetNodeId = versionId.flatMap(id -> mapRouteService.findStationNodeId(target.getId(), id));
        if (targetNodeId.isEmpty()) {
            return Plan.UNMANAGED;
        }

        // 물류센터 단위로 직렬화한 뒤 위치·예약을 잠금 조회로 읽는다(아래 읽기는 모두 이 잠금을 얻은 뒤의 최신 값)
        siteRepository.findByIdForUpdate(siteId);
        List<Long> siteRobotIds = robotRepository.findIdsBySiteId(siteId);
        Map<Long, RobotCurrentState> states = robotCurrentStateRepository.findByRobotIdInForShare(siteRobotIds).stream()
                .collect(Collectors.toMap(RobotCurrentState::getRobotId, Function.identity()));
        RobotCurrentState own = states.get(robot.getId());
        if (own == null || own.getCurrentNodeId() == null) {
            return Plan.UNMANAGED;
        }
        MapRouteService.RouteGraph graph = mapRouteService.loadGraph(versionId.get());
        List<Waypoint> direct = graph.shortestPath(own.getCurrentNodeId(), targetNodeId.get());
        if (direct.isEmpty()) {
            return Plan.UNMANAGED; // 막힘과 관계없이 갈 길이 없다 — 로봇이 판단하게 예전처럼 보낸다
        }

        // 피할 노드: 다른 로봇이 서 있는 노드와 다른 로봇의 경로 예약(자기 현재 노드는 제외 — 겹쳐 있으면 떠나는 쪽을 막지 않는다)
        Map<Long, String> blockers = new LinkedHashMap<>();
        for (RobotCurrentState other : states.values()) {
            if (!other.getRobotId().equals(robot.getId()) && other.getCurrentNodeId() != null && !other.getCurrentNodeId().equals(own.getCurrentNodeId())) {
                blockers.putIfAbsent(other.getCurrentNodeId(), robotCode(other.getRobotId()) + "이(가) 서 있는");
            }
        }
        for (ResourceReservation held : resourceReservationRepository.findHeldNodeReservationsOfRobotsForShare(siteRobotIds)) {
            if (!held.getRobot().getId().equals(robot.getId()) && !held.getResourceId().equals(own.getCurrentNodeId())) {
                blockers.putIfAbsent(held.getResourceId(), held.getRobot().getCode() + "이(가) 지나갈 예정인");
            }
        }

        List<Waypoint> path = graph.shortestPathAvoiding(own.getCurrentNodeId(), targetNodeId.get(), blockers.keySet());
        if (path.isEmpty()) {
            // 돌아갈 길이 없다 — 막는 로봇이 비킬 때까지 기다린다. 사유는 원래 최단 경로에서 처음 막힌 노드
            Long firstBlocked = direct.stream().map(Waypoint::nodeId).filter(blockers::containsKey).findFirst().orElse(targetNodeId.get());
            String nodeCode = nodeRepository.findById(firstBlocked).map(Node::getCode).orElse(String.valueOf(firstBlocked));
            return Plan.waiting(blockers.getOrDefault(firstBlocked, "다른 로봇이 쓰는") + " 노드 " + nodeCode + "을(를) 피해 갈 길이 없습니다");
        }
        Set<Long> pathNodeIds = path.stream().map(Waypoint::nodeId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> codes = nodeRepository.findAllById(pathNodeIds).stream().collect(Collectors.toMap(Node::getId, Node::getCode));

        releaseNodes(robot);
        for (Long nodeId : pathNodeIds) {
            resourceReservationRepository.save(new ResourceReservation(ResourceType.NODE, nodeId, robot, step));
        }
        List<String> route = path.stream().map(waypoint -> codes.get(waypoint.nodeId())).toList();
        return Plan.clear(route, jsonMapper.writeValueAsString(Map.of("route", route)));
    }

    /** 교통 대기가 이 시간(초)을 넘으면 TRAFFIC_BLOCKED 알람을 올린다. */
    public long blockedAlarmSeconds() {
        return blockedAlarmSeconds;
    }

    /** 이 로봇의 경로 노드 예약을 모두 푼다(작업장 예약은 그대로). */
    public void releaseNodes(Robot robot) {
        List<ResourceReservation> held = resourceReservationRepository.findByRobotIdAndResourceTypeAndStatus(
                robot.getId(), ResourceType.NODE, ResourceReservationStatus.HELD);
        held.forEach(ResourceReservation::release);
        if (!held.isEmpty()) {
            // 길이 비었다 — 커밋 뒤 기다리던 명령을 바로(오래 기다린 순서로) 다시 계획하게 한다. 주기(2초)만 기다리면 그 사이 새로 들어온
            // 이동이 방금 빈 길을 먼저 차지해, 오래 기다린 로봇이 계속 밀릴 수 있다(가상 로봇으로 검증하다 발견).
            eventPublisher.publishEvent(new NodesReleased(robot.getSite().getId()));
        }
    }

    /** 46단계: 경로 예약이 풀렸다(커밋 뒤 교통 대기 재시도의 신호). */
    public record NodesReleased(Long siteId) {
    }

    private String robotCode(Long robotId) {
        return robotRepository.findById(robotId).map(Robot::getCode).orElse("로봇 " + robotId);
    }

    public enum Outcome { UNMANAGED, CLEAR, WAIT }

    /**
     * @param route       CLEAR일 때 지나갈 노드 코드(출발 노드 포함)
     * @param payloadJson CLEAR일 때 명령에 담을 경로 JSON({@code {"route":[...]}})
     * @param blockReason WAIT일 때 사유
     */
    public record Plan(Outcome outcome, List<String> route, String payloadJson, String blockReason) {
        static final Plan UNMANAGED = new Plan(Outcome.UNMANAGED, List.of(), null, null);

        static Plan clear(List<String> route, String payloadJson) {
            return new Plan(Outcome.CLEAR, route, payloadJson, null);
        }

        static Plan waiting(String reason) {
            return new Plan(Outcome.WAIT, List.of(), null, reason);
        }

        public boolean isWaiting() {
            return outcome == Outcome.WAIT;
        }
    }
}
