package com.amrproject.service.map;

import com.amrproject.domain.entity.map.Edge;
import com.amrproject.domain.entity.map.EdgeDirection;
import com.amrproject.domain.entity.map.MapEntity;
import com.amrproject.domain.entity.map.MapVersionStatus;
import com.amrproject.domain.entity.map.Node;
import com.amrproject.repository.map.EdgeRepository;
import com.amrproject.repository.map.MapRepository;
import com.amrproject.repository.map.MapVersionRepository;
import com.amrproject.repository.map.NodeRepository;
import com.amrproject.repository.site.StationMapBindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * 30단계: 활성 지도 위의 위치·경로 조회. 서버는 로봇에게 여전히 "이 작업장으로 가라"만 지시하고 경로를 내려보내지 않는다
 * (DESIGN.md §3 책임 분리 — 경로 추종은 로봇의 몫). 이 서비스는 시뮬레이터가 "실제 로봇이라면 내부에서 했을" 이동을 흉내 내
 * 화면에 보여줄 때, 대시보드가 로봇 위치를 지도 좌표로 바꿀 때, 그리고 38단계부터 디스패치가 가까운 로봇을 고를 때(거리만) 쓴다.
 *
 * <p>24단계 검증이 활성 지도의 모든 통로가 장애물을 지나지 않음을 보장하므로, 통로 그래프 위의 최단 경로는
 * 자동으로 장애물을 피한다.
 */
@Service
@Transactional(readOnly = true)
public class MapRouteService {

    /** 화면 표시용 경유점 — 트랜잭션 밖(애니메이션 스레드)에서 쓰도록 엔티티 대신 값만 담는다. */
    public record Waypoint(Long nodeId, double x, double y) {
    }

    private final MapRepository mapRepository;
    private final MapVersionRepository mapVersionRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final StationMapBindingRepository stationMapBindingRepository;

    public MapRouteService(
            MapRepository mapRepository,
            MapVersionRepository mapVersionRepository,
            NodeRepository nodeRepository,
            EdgeRepository edgeRepository,
            StationMapBindingRepository stationMapBindingRepository
    ) {
        this.mapRepository = mapRepository;
        this.mapVersionRepository = mapVersionRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.stationMapBindingRepository = stationMapBindingRepository;
    }

    /** 물류센터의 활성(ACTIVE) 지도 버전. 지도가 여러 개면 id가 가장 작은 지도의 활성 버전을 쓴다. */
    public Optional<Long> findActiveVersionId(Long siteId) {
        return mapRepository.findBySiteId(siteId).stream()
                .sorted(Comparator.comparing(MapEntity::getId))
                .map(map -> mapVersionRepository.findByMapIdAndStatus(map.getId(), MapVersionStatus.ACTIVE))
                .flatMap(Optional::stream)
                .map(version -> version.getId())
                .findFirst();
    }

    /** 작업장이 해당 지도 버전에서 연결된 노드(24단계 작업장-지도 바인딩). */
    public Optional<Long> findStationNodeId(Long stationId, Long mapVersionId) {
        return stationMapBindingRepository.findByStationIdAndMapVersionId(stationId, mapVersionId)
                .map(binding -> binding.getNode().getId());
    }

    /**
     * 통로 그래프 위 최단 경로(Dijkstra). 가중치는 통로의 distance, 없으면 두 노드의 직선 거리. ONE_WAY 통로는
     * from→to로만 지난다. 출발·도착 노드를 포함한 경유점 목록을 반환하고, 갈 수 없으면 빈 목록을 반환한다.
     */
    public List<Waypoint> shortestPath(Long mapVersionId, Long fromNodeId, Long toNodeId) {
        return loadGraph(mapVersionId).shortestPath(fromNodeId, toNodeId);
    }

    /**
     * 38단계: 지도 버전의 통로 그래프를 한 번 읽어 둔다. 디스패치 사이클처럼 같은 지도에서 거리를 여러 번 계산할 때
     * 작업마다 노드·통로를 다시 조회하지 않도록 한다.
     */
    public RouteGraph loadGraph(Long mapVersionId) {
        Map<Long, Waypoint> waypoints = new HashMap<>();
        for (Node node : nodeRepository.findByMapVersionId(mapVersionId)) {
            waypoints.put(node.getId(), new Waypoint(node.getId(), node.getX().doubleValue(), node.getY().doubleValue()));
        }
        RouteGraph graph = new RouteGraph(waypoints);
        for (Edge edge : edgeRepository.findByMapVersionId(mapVersionId)) {
            Long from = edge.getFromNode().getId();
            Long to = edge.getToNode().getId();
            double weight = edge.getDistance() != null
                    ? edge.getDistance().doubleValue()
                    : distance(waypoints.get(from), waypoints.get(to));
            graph.addArc(from, to, weight);
            if (edge.getDirection() == EdgeDirection.TWO_WAY) {
                graph.addArc(to, from, weight);
            }
        }
        return graph;
    }

    /** 방향이 있는 통로 그래프. 트랜잭션 밖에서도 쓸 수 있게 값만 담는다. */
    public static final class RouteGraph {

        private final Map<Long, Waypoint> waypoints;
        /** from → (to → 가중치). 같은 두 노드 사이 통로가 여러 개면 가장 짧은 것 */
        private final Map<Long, Map<Long, Double>> outgoing = new HashMap<>();
        /** to → (from → 가중치). 한 목적지까지의 거리를 모든 노드에 대해 한 번에 구할 때(역방향 탐색) 쓴다 */
        private final Map<Long, Map<Long, Double>> incoming = new HashMap<>();

        private RouteGraph(Map<Long, Waypoint> waypoints) {
            this.waypoints = waypoints;
        }

        private void addArc(Long from, Long to, double weight) {
            outgoing.computeIfAbsent(from, key -> new HashMap<>()).merge(to, weight, Math::min);
            incoming.computeIfAbsent(to, key -> new HashMap<>()).merge(from, weight, Math::min);
        }

        public boolean containsNode(Long nodeId) {
            return waypoints.containsKey(nodeId);
        }

        public List<Waypoint> shortestPath(Long fromNodeId, Long toNodeId) {
            if (!waypoints.containsKey(fromNodeId) || !waypoints.containsKey(toNodeId)) {
                return List.of();
            }
            Map<Long, Long> previous = new HashMap<>();
            Map<Long, Double> best = dijkstra(fromNodeId, outgoing, previous, toNodeId);
            if (!best.containsKey(toNodeId)) {
                return List.of();
            }
            List<Waypoint> path = new ArrayList<>();
            for (Long node = toNodeId; node != null; node = previous.get(node)) {
                path.add(waypoints.get(node));
            }
            Collections.reverse(path);
            return path;
        }

        /** 42단계: 출발 노드에서 모든 노드까지의 최단 거리(통로 방향을 따라). 갈 수 없는 노드는 결과에 없다(출발 자신은 0). */
        public Map<Long, Double> distancesFrom(Long sourceNodeId) {
            if (!waypoints.containsKey(sourceNodeId)) {
                return Map.of();
            }
            return dijkstra(sourceNodeId, outgoing, new HashMap<>(), null);
        }

        /**
         * 38단계: 모든 노드에서 목적지 노드까지의 최단 거리. 통로를 거꾸로 따라 목적지에서 한 번만 탐색하므로 로봇이 여러 대여도
         * 로봇마다 경로를 따로 구하지 않는다. 목적지로 갈 수 없는 노드는 결과에 없다(목적지 자신은 0).
         */
        public Map<Long, Double> distancesTo(Long targetNodeId) {
            if (!waypoints.containsKey(targetNodeId)) {
                return Map.of();
            }
            return dijkstra(targetNodeId, incoming, new HashMap<>(), null);
        }

        private static Map<Long, Double> dijkstra(Long start, Map<Long, Map<Long, Double>> arcs, Map<Long, Long> previous, Long stopAt) {
            Map<Long, Double> best = new HashMap<>();
            PriorityQueue<Map.Entry<Long, Double>> queue = new PriorityQueue<>(Map.Entry.comparingByValue());
            best.put(start, 0.0);
            queue.add(Map.entry(start, 0.0));
            while (!queue.isEmpty()) {
                Map.Entry<Long, Double> current = queue.poll();
                Long node = current.getKey();
                if (current.getValue() > best.getOrDefault(node, Double.MAX_VALUE)) {
                    continue;
                }
                if (node.equals(stopAt)) {
                    break;
                }
                for (Map.Entry<Long, Double> arc : arcs.getOrDefault(node, Map.of()).entrySet()) {
                    double candidate = current.getValue() + arc.getValue();
                    if (candidate < best.getOrDefault(arc.getKey(), Double.MAX_VALUE)) {
                        best.put(arc.getKey(), candidate);
                        previous.put(arc.getKey(), node);
                        queue.add(Map.entry(arc.getKey(), candidate));
                    }
                }
            }
            return best;
        }
    }

    private static double distance(Waypoint a, Waypoint b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }
}
