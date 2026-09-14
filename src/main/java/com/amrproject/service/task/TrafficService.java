package com.amrproject.service.task;

import com.amrproject.domain.entity.map.Node;
import com.amrproject.domain.entity.map.Zone;
import com.amrproject.domain.entity.map.ZoneType;
import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.LocalizationState;
import com.amrproject.domain.entity.robot.MotionState;
import com.amrproject.domain.entity.robot.OperationMode;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotCommandStatus;
import com.amrproject.domain.entity.robot.RobotCommandType;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.robot.SafetyState;
import com.amrproject.domain.entity.site.Station;
import com.amrproject.domain.entity.site.StationMapBinding;
import com.amrproject.domain.entity.site.StationType;
import com.amrproject.domain.entity.task.ResourceReservation;
import com.amrproject.domain.entity.task.ResourceReservationStatus;
import com.amrproject.domain.entity.task.ResourceType;
import com.amrproject.domain.entity.task.TaskStep;
import com.amrproject.repository.map.NodeRepository;
import com.amrproject.repository.map.ZoneRepository;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.repository.site.SiteRepository;
import com.amrproject.repository.site.StationMapBindingRepository;
import com.amrproject.repository.task.ResourceReservationRepository;
import com.amrproject.service.map.MapRouteService;
import com.amrproject.service.map.MapRouteService.Waypoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *
 * <p>47단계:
 * <ul>
 *   <li><b>지나간 노드 반납</b>({@link #releasePassedNodes}) — 로봇이 경로의 다음 노드에 도착했다고 보고하면 그 앞 노드 예약을 푼다.
 *       예전엔 도착할 때까지 경로 전체를 쥐고 있어, 뒤따르는 로봇이 이미 비어 있는 통로 앞에서 기다렸다.</li>
 *   <li><b>비키기</b>({@link #planReposition}) — 기다리는 로봇의 길을 할 일 없이 서 있는 로봇(또는 더 늦게 기다리기 시작한 대기 로봇)이
 *       막고 있으면, 그 로봇을 길 밖의 가까운 노드(대기 작업장 노드 우선)로 보낸다. 서로의 자리를 기다리는 교착도 늦게 기다린 쪽이
 *       비켜 풀린다. 비키는 이동은 작업 단계가 없는 경로 예약(task_step_id 없음)으로 표시하고, 그동안 그 로봇의 이동 계획은 기다린다.</li>
 * </ul>
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
    private final StationMapBindingRepository stationMapBindingRepository;
    private final ZoneRepository zoneRepository;
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
            StationMapBindingRepository stationMapBindingRepository,
            ZoneRepository zoneRepository,
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
        this.stationMapBindingRepository = stationMapBindingRepository;
        this.zoneRepository = zoneRepository;
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
        List<ResourceReservation> heldNodes = resourceReservationRepository.findHeldNodeReservationsOfRobotsForShare(siteRobotIds);
        // 47단계: 다른 로봇의 길을 비키는 중이면 그 이동이 끝날 때까지 기다린다(끝나면 예약이 풀려 다시 계획된다)
        if (heldNodes.stream().anyMatch(held -> held.getRobot().getId().equals(robot.getId()) && held.getTaskStep() == null)) {
            return Plan.waiting(robot.getCode() + "이(가) 다른 로봇의 길을 비키는 중입니다");
        }

        // 피할 노드: 다른 로봇이 서 있는 노드와 다른 로봇의 경로 예약(자기 현재 노드는 제외 — 겹쳐 있으면 떠나는 쪽을 막지 않는다)
        Map<Long, String> blockers = occupiedNodes(states, heldNodes, robot.getId(), own.getCurrentNodeId());

        List<Waypoint> path = graph.shortestPathAvoiding(own.getCurrentNodeId(), targetNodeId.get(), blockers.keySet());
        if (path.isEmpty()) {
            // 돌아갈 길이 없다 — 막는 로봇이 비킬 때까지 기다린다. 사유는 원래 최단 경로에서 처음 막힌 노드
            Long firstBlocked = direct.stream().map(Waypoint::nodeId).filter(blockers::containsKey).findFirst().orElse(targetNodeId.get());
            String nodeCode = nodeRepository.findById(firstBlocked).map(Node::getCode).orElse(String.valueOf(firstBlocked));
            return Plan.waiting(blockers.getOrDefault(firstBlocked, "다른 로봇이 쓰는") + " 노드 " + nodeCode + "을(를) 피해 갈 길이 없습니다");
        }
        releaseNodes(robot);
        return reserve(robot, step, path);
    }

    /** 경로 노드를 순서대로 예약하고(id 순서 = 경로 순서, 지나간 노드 반납이 이 순서를 쓴다) 경로를 담은 CLEAR 계획을 만든다. */
    private Plan reserve(Robot robot, TaskStep step, List<Waypoint> path) {
        Set<Long> pathNodeIds = path.stream().map(Waypoint::nodeId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> codes = nodeRepository.findAllById(pathNodeIds).stream().collect(Collectors.toMap(Node::getId, Node::getCode));
        for (Long nodeId : pathNodeIds) {
            resourceReservationRepository.save(new ResourceReservation(ResourceType.NODE, nodeId, robot, step));
        }
        List<String> route = path.stream().map(waypoint -> codes.get(waypoint.nodeId())).toList();
        return Plan.clear(route, jsonMapper.writeValueAsString(Map.of("route", route)));
    }

    /**
     * robotId 로봇이 피해야 할 노드와 사유: 다른 로봇이 서 있는 노드와 다른 로봇의 경로 예약. 자기 현재 노드(ownNodeId)는 넣지 않는다 —
     * 겹쳐 있으면 떠나는 쪽을 막지 않는다.
     */
    private Map<Long, String> occupiedNodes(Map<Long, RobotCurrentState> states, List<ResourceReservation> heldNodes, Long robotId, Long ownNodeId) {
        Map<Long, String> blockers = new LinkedHashMap<>();
        for (RobotCurrentState other : states.values()) {
            if (!other.getRobotId().equals(robotId) && other.getCurrentNodeId() != null && !other.getCurrentNodeId().equals(ownNodeId)) {
                blockers.putIfAbsent(other.getCurrentNodeId(), robotCode(other.getRobotId()) + "이(가) 서 있는");
            }
        }
        for (ResourceReservation held : heldNodes) {
            if (!held.getRobot().getId().equals(robotId) && !held.getResourceId().equals(ownNodeId)) {
                blockers.putIfAbsent(held.getResourceId(), held.getRobot().getCode() + "이(가) 지나갈 예정인");
            }
        }
        return blockers;
    }

    /** 47단계: 서버가 보내 움직여도 되는 상태 — 연결됨, 자동 모드, 안전 정상, 위치 앎, 멈춰 있음(충전·이동 중 아님). */
    private static boolean canMove(RobotCurrentState state) {
        return state.getConnectionState() == ConnectionState.ONLINE
                && state.getOperationMode() == OperationMode.AUTO
                && state.getSafetyState() == SafetyState.NORMAL
                && state.getLocalizationState() == LocalizationState.LOCALIZED
                && state.getMotionState() == MotionState.IDLE;
    }

    /**
     * 47단계: 기다리는 로봇(requester)의 길을 막는 blocker를 길 밖으로 비키게 할 경로를 계획한다. 호출자가 blocker의 작업 조건(할 일 없음,
     * 또는 requester보다 늦게 교통 대기를 시작함)을 확인한 뒤 부른다. CLEAR면 blocker 이름으로(작업 단계 없이) 경로 노드를 예약한 뒤다.
     *
     * <p>비키지 않는 경우(UNMANAGED): requester가 이미 갈 수 있음, blocker를 비켜도 길이 나지 않음, blocker가 움직일 수 없는 상태(연결·자동 모드·
     * 안전·위치 추정·정지 중이 아님)이거나 이미 경로를 예약함, 갈 곳 없음. 목적지는 requester가 쓸 경로 밖이면서 다른 로봇이 쓰지 않는 노드 중
     * 대기(WAITING) 작업장 노드 → 작업장이 연결되지 않은 노드 → 그 밖의 작업장 노드 순, 같은 순위면 가까운 곳이다.
     */
    public Plan planReposition(Robot requester, Station target, Robot blocker) {
        if (!enabled || target == null || !requester.getSite().getId().equals(blocker.getSite().getId())) {
            return Plan.UNMANAGED;
        }
        Long siteId = requester.getSite().getId();
        Optional<Long> versionId = mapRouteService.findActiveVersionId(siteId);
        Optional<Long> targetNodeId = versionId.flatMap(id -> mapRouteService.findStationNodeId(target.getId(), id));
        if (targetNodeId.isEmpty()) {
            return Plan.UNMANAGED;
        }

        siteRepository.findByIdForUpdate(siteId);
        List<Long> siteRobotIds = robotRepository.findIdsBySiteId(siteId);
        Map<Long, RobotCurrentState> states = robotCurrentStateRepository.findByRobotIdInForShare(siteRobotIds).stream()
                .collect(Collectors.toMap(RobotCurrentState::getRobotId, Function.identity()));
        RobotCurrentState own = states.get(requester.getId());
        RobotCurrentState moving = states.get(blocker.getId());
        if (own == null || own.getCurrentNodeId() == null || moving == null || moving.getCurrentNodeId() == null || !canMove(moving)) {
            return Plan.UNMANAGED;
        }
        List<ResourceReservation> heldNodes = resourceReservationRepository.findHeldNodeReservationsOfRobotsForShare(siteRobotIds);
        if (heldNodes.stream().anyMatch(held -> held.getRobot().getId().equals(blocker.getId()))) {
            return Plan.UNMANAGED; // 이미 이동 중이거나 비키는 중
        }

        MapRouteService.RouteGraph graph = mapRouteService.loadGraph(versionId.get());
        Map<Long, String> forRequester = occupiedNodes(states, heldNodes, requester.getId(), own.getCurrentNodeId());
        if (!graph.shortestPathAvoiding(own.getCurrentNodeId(), targetNodeId.get(), forRequester.keySet()).isEmpty()) {
            return Plan.UNMANAGED; // 이미 길이 있다 — 다음 재시도에서 출발한다
        }
        Set<Long> withoutBlocker = new LinkedHashSet<>(forRequester.keySet());
        withoutBlocker.remove(moving.getCurrentNodeId());
        List<Waypoint> needed = graph.shortestPathAvoiding(own.getCurrentNodeId(), targetNodeId.get(), withoutBlocker);
        if (needed.isEmpty()) {
            return Plan.UNMANAGED; // 이 로봇이 비켜도 다른 로봇 때문에 길이 없다
        }
        Set<Long> neededNodeIds = needed.stream().map(Waypoint::nodeId).collect(Collectors.toSet());

        // 비키는 로봇이 피할 노드: 다른 로봇(기다리는 로봇 포함)이 서 있거나 예약한 노드
        Set<Long> avoid = occupiedNodes(states, heldNodes, blocker.getId(), moving.getCurrentNodeId()).keySet();
        Map<Long, Double> reachable = graph.distancesFromAvoiding(moving.getCurrentNodeId(), avoid);
        Map<Long, Integer> stationNodeRank = new HashMap<>();
        for (StationMapBinding binding : stationMapBindingRepository.findByMapVersionIdWithStation(versionId.get())) {
            int rank = binding.getStation().getStationType() == StationType.WAITING ? 0 : 2;
            stationNodeRank.merge(binding.getNode().getId(), rank, Math::min);
        }
        // 48단계: 대기 구역(PARKING) 노드도 대기 작업장 노드와 같은 1순위
        zoneRepository.findWithNodesByMapVersionIdAndZoneType(versionId.get(), ZoneType.PARKING)
                .forEach(zone -> zone.getNodes().forEach(node -> stationNodeRank.merge(node.getId(), 0, Math::min)));
        Optional<Long> destination = reachable.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(moving.getCurrentNodeId()) && !neededNodeIds.contains(entry.getKey()) && !avoid.contains(entry.getKey()))
                .sorted(Comparator.comparing((Map.Entry<Long, Double> entry) -> stationNodeRank.getOrDefault(entry.getKey(), 1))
                        .thenComparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .findFirst();
        if (destination.isEmpty()) {
            return Plan.UNMANAGED;
        }
        List<Waypoint> path = graph.shortestPathAvoiding(moving.getCurrentNodeId(), destination.get(), avoid);
        return reserve(blocker, null, path);
    }

    /**
     * 48단계: 할 일 없는 로봇을 대기 구역(PARKING)의 빈 노드로 보낼 경로를 계획한다. CLEAR면 작업 단계 없이 경로 노드를 예약한 뒤다.
     *
     * <p>구역을 고르게 채운다: 구역마다 쓰는 중인 노드(다른 로봇이 서 있거나 예약한 노드) 수 ÷ 수용량(max_capacity, 없으면 노드 수)이
     * 가장 낮은 구역, 같으면 가장 가까운 빈 노드가 있는 구역의 그 노드다. 가득 찬 구역과 갈 수 없는(다른 로봇에 막힌) 노드는 뺀다.
     *
     * <p>보내지 않는 경우(UNMANAGED): 교통 관리·대기 구역 없음, 이미 대기 구역에 있음, 위치 모름·움직일 수 없는 상태, 배터리가
     * lowBatteryPct 이하(충전 대상), 이미 경로를 예약함(이동·비키는 중), 갈 곳 없음.
     */
    public Plan planParking(Robot robot, int lowBatteryPct) {
        if (!enabled) {
            return Plan.UNMANAGED;
        }
        Long siteId = robot.getSite().getId();
        Optional<Long> versionId = mapRouteService.findActiveVersionId(siteId);
        List<Zone> zones = versionId.map(id -> zoneRepository.findWithNodesByMapVersionIdAndZoneType(id, ZoneType.PARKING)).orElse(List.of());
        Map<Long, Zone> zoneByNode = new HashMap<>();
        zones.forEach(zone -> zone.getNodes().forEach(node -> zoneByNode.putIfAbsent(node.getId(), zone)));
        Optional<Long> standingOn = robotCurrentStateRepository.findCurrentNodeId(robot.getId());
        if (zoneByNode.isEmpty() || standingOn.isEmpty() || zoneByNode.containsKey(standingOn.get())) {
            return Plan.UNMANAGED; // 잠그기 전 가벼운 확인 — 대부분의 사이클은 여기서 끝난다
        }

        siteRepository.findByIdForUpdate(siteId);
        List<Long> siteRobotIds = robotRepository.findIdsBySiteId(siteId);
        Map<Long, RobotCurrentState> states = robotCurrentStateRepository.findByRobotIdInForShare(siteRobotIds).stream()
                .collect(Collectors.toMap(RobotCurrentState::getRobotId, Function.identity()));
        RobotCurrentState own = states.get(robot.getId());
        if (own == null || own.getCurrentNodeId() == null || zoneByNode.containsKey(own.getCurrentNodeId()) || !canMove(own)
                || own.getBatteryPct() <= lowBatteryPct) {
            return Plan.UNMANAGED;
        }
        List<ResourceReservation> heldNodes = resourceReservationRepository.findHeldNodeReservationsOfRobotsForShare(siteRobotIds);
        if (heldNodes.stream().anyMatch(held -> held.getRobot().getId().equals(robot.getId()))) {
            return Plan.UNMANAGED;
        }

        Set<Long> occupied = occupiedNodes(states, heldNodes, robot.getId(), own.getCurrentNodeId()).keySet();
        MapRouteService.RouteGraph graph = mapRouteService.loadGraph(versionId.get());
        Map<Long, Double> reachable = graph.distancesFromAvoiding(own.getCurrentNodeId(), occupied);
        Long bestNode = null;
        double bestLoad = Double.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (Zone zone : zones) {
            int capacity = zone.getMaxCapacity() != null ? Math.min(zone.getMaxCapacity(), zone.getNodes().size()) : zone.getNodes().size();
            long used = zone.getNodes().stream().filter(node -> occupied.contains(node.getId())).count();
            if (capacity == 0 || used >= capacity) {
                continue;
            }
            double load = (double) used / capacity;
            Optional<Map.Entry<Long, Double>> nearestFree = zone.getNodes().stream()
                    .filter(node -> !occupied.contains(node.getId()) && reachable.containsKey(node.getId()))
                    .map(node -> Map.entry(node.getId(), reachable.get(node.getId())))
                    .min(Map.Entry.comparingByValue());
            if (nearestFree.isEmpty()) {
                continue;
            }
            if (load < bestLoad || (load == bestLoad && nearestFree.get().getValue() < bestDistance)) {
                bestNode = nearestFree.get().getKey();
                bestLoad = load;
                bestDistance = nearestFree.get().getValue();
            }
        }
        if (bestNode == null) {
            return Plan.UNMANAGED;
        }
        return reserve(robot, null, graph.shortestPathAvoiding(own.getCurrentNodeId(), bestNode, occupied));
    }

    /** 47·48단계: 작업 없는 이동(비키기·대기 구역)이 성공·실패로 끝났으면 경로 예약을 푼다. 결과 불명이면 로봇이 경로 어딘가에 있을 수 있어 유지한다. */
    public void releaseNodesIfRepositionFinished(RobotCommand command) {
        if (command.getCommandType() == RobotCommandType.REPOSITION
                && (command.getStatus() == RobotCommandStatus.SUCCEEDED || command.getStatus() == RobotCommandStatus.FAILED)) {
            releaseNodes(command.getRobot());
        }
    }

    /**
     * 47단계: 로봇이 경로의 한 노드에 도착했다고 보고했다 — 예약한 경로에서 그 노드보다 앞의 노드 예약을 푼다(현재 노드는 로봇이 서 있어 계속 막는다).
     * 예약은 경로 순서대로 저장되므로 id 순서가 경로 순서다. 경로에 없는 노드면 아무것도 하지 않는다.
     */
    public void releasePassedNodes(Robot robot, Long currentNodeId) {
        if (currentNodeId == null) {
            return;
        }
        List<ResourceReservation> held = resourceReservationRepository.findByRobotIdAndResourceTypeAndStatus(
                        robot.getId(), ResourceType.NODE, ResourceReservationStatus.HELD).stream()
                .sorted(Comparator.comparing(ResourceReservation::getId))
                .toList();
        int reached = -1;
        for (int i = 0; i < held.size(); i++) {
            if (held.get(i).getResourceId().equals(currentNodeId)) {
                reached = i;
                break;
            }
        }
        if (reached <= 0) {
            return;
        }
        held.subList(0, reached).forEach(ResourceReservation::release);
        eventPublisher.publishEvent(new NodesReleased(robot.getSite().getId()));
    }

    /** 47단계: 기다리는 로봇의 원래 최단 경로(막힘 무시) 위에 서 있는 다른 로봇 id — 비키기 후보, 경로 순서. 잠그지 않고 읽는다. */
    public List<Long> robotsStandingOnRoute(Robot robot, Station target) {
        if (!enabled || target == null) {
            return List.of();
        }
        Long siteId = robot.getSite().getId();
        Optional<Long> versionId = mapRouteService.findActiveVersionId(siteId);
        Optional<Long> targetNodeId = versionId.flatMap(id -> mapRouteService.findStationNodeId(target.getId(), id));
        Map<Long, RobotCurrentState> states = robotCurrentStateRepository.findAllById(robotRepository.findIdsBySiteId(siteId)).stream()
                .collect(Collectors.toMap(RobotCurrentState::getRobotId, Function.identity()));
        RobotCurrentState own = states.get(robot.getId());
        if (targetNodeId.isEmpty() || own == null || own.getCurrentNodeId() == null) {
            return List.of();
        }
        Map<Long, Long> robotByNode = new HashMap<>();
        states.values().stream()
                .filter(state -> !state.getRobotId().equals(robot.getId()) && state.getCurrentNodeId() != null)
                .forEach(state -> robotByNode.putIfAbsent(state.getCurrentNodeId(), state.getRobotId()));
        return mapRouteService.loadGraph(versionId.get()).shortestPath(own.getCurrentNodeId(), targetNodeId.get()).stream()
                .map(waypoint -> robotByNode.get(waypoint.nodeId()))
                .filter(Objects::nonNull)
                .toList();
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
