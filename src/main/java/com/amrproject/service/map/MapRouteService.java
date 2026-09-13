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
 * 30단계: 활성 지도 위의 위치·경로 조회. 서버의 작업·배정 로직은 여전히 "이 작업장으로 가라"만 지시하고
 * 경로를 쓰지 않는다(DESIGN.md §3 책임 분리 — 경로 추종은 로봇의 몫). 이 서비스는 시뮬레이터가 "실제 로봇이라면
 * 내부에서 했을" 이동을 흉내 내 화면에 보여줄 때와, 대시보드가 로봇 위치를 지도 좌표로 바꿀 때만 쓴다.
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
        Map<Long, Waypoint> waypoints = new HashMap<>();
        for (Node node : nodeRepository.findByMapVersionId(mapVersionId)) {
            waypoints.put(node.getId(), new Waypoint(node.getId(), node.getX().doubleValue(), node.getY().doubleValue()));
        }
        if (!waypoints.containsKey(fromNodeId) || !waypoints.containsKey(toNodeId)) {
            return List.of();
        }

        Map<Long, List<Long>> neighbors = new HashMap<>();
        Map<String, Double> weights = new HashMap<>();
        for (Edge edge : edgeRepository.findByMapVersionId(mapVersionId)) {
            Long from = edge.getFromNode().getId();
            Long to = edge.getToNode().getId();
            double weight = edge.getDistance() != null
                    ? edge.getDistance().doubleValue()
                    : distance(waypoints.get(from), waypoints.get(to));
            addArc(neighbors, weights, from, to, weight);
            if (edge.getDirection() == EdgeDirection.TWO_WAY) {
                addArc(neighbors, weights, to, from, weight);
            }
        }

        Map<Long, Double> best = new HashMap<>();
        Map<Long, Long> previous = new HashMap<>();
        PriorityQueue<Map.Entry<Long, Double>> queue = new PriorityQueue<>(Map.Entry.comparingByValue());
        best.put(fromNodeId, 0.0);
        queue.add(Map.entry(fromNodeId, 0.0));
        while (!queue.isEmpty()) {
            Map.Entry<Long, Double> current = queue.poll();
            Long node = current.getKey();
            if (current.getValue() > best.getOrDefault(node, Double.MAX_VALUE)) {
                continue;
            }
            if (node.equals(toNodeId)) {
                break;
            }
            for (Long next : neighbors.getOrDefault(node, List.of())) {
                double candidate = current.getValue() + weights.get(node + ">" + next);
                if (candidate < best.getOrDefault(next, Double.MAX_VALUE)) {
                    best.put(next, candidate);
                    previous.put(next, node);
                    queue.add(Map.entry(next, candidate));
                }
            }
        }
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

    private static void addArc(Map<Long, List<Long>> neighbors, Map<String, Double> weights, Long from, Long to, double weight) {
        neighbors.computeIfAbsent(from, key -> new ArrayList<>()).add(to);
        weights.merge(from + ">" + to, weight, Math::min);
    }

    private static double distance(Waypoint a, Waypoint b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }
}
