package com.amrproject.service;

import com.amrproject.domain.entity.map.Edge;
import com.amrproject.domain.entity.map.MapEntity;
import com.amrproject.domain.entity.map.MapVersion;
import com.amrproject.domain.entity.map.MapVersionStatus;
import com.amrproject.domain.entity.map.Node;
import com.amrproject.domain.entity.site.Station;
import com.amrproject.domain.entity.site.StationMapBinding;
import com.amrproject.domain.entity.map.Zone;
import com.amrproject.dto.request.site.BindStationMapRequest;
import com.amrproject.dto.request.map.CreateEdgeRequest;
import com.amrproject.dto.request.map.CreateMapVersionRequest;
import com.amrproject.dto.request.map.CreateNodeRequest;
import com.amrproject.dto.request.map.CreateZoneRequest;
import com.amrproject.dto.response.map.MapVersionResponse;
import com.amrproject.dto.response.map.MapVersionValidationResponse;
import com.amrproject.exception.DuplicateResourceException;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.exception.InvalidStateTransitionException;
import com.amrproject.repository.map.EdgeRepository;
import com.amrproject.repository.map.MapVersionRepository;
import com.amrproject.repository.map.NodeRepository;
import com.amrproject.repository.site.StationMapBindingRepository;
import com.amrproject.repository.map.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 지도 버전의 노드/통로/구역 저작과 검증·활성화를 담당한다 (6단계).
 * 실제 지도 파일 파싱은 지도 파일 형식이 미정이라(docs/DESIGN.md) 구현하지 않고,
 * 이 서비스의 add* 메서드를 임시 저작 도구로 사용한다 (docs/API.md §2.3 참고).
 */
@Service
@Transactional(readOnly = true)
public class MapVersionService {

    private final MapVersionRepository mapVersionRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final ZoneRepository zoneRepository;
    private final StationMapBindingRepository stationMapBindingRepository;
    private final MapService mapService;
    private final StationService stationService;

    public MapVersionService(
            MapVersionRepository mapVersionRepository,
            NodeRepository nodeRepository,
            EdgeRepository edgeRepository,
            ZoneRepository zoneRepository,
            StationMapBindingRepository stationMapBindingRepository,
            MapService mapService,
            StationService stationService
    ) {
        this.mapVersionRepository = mapVersionRepository;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.zoneRepository = zoneRepository;
        this.stationMapBindingRepository = stationMapBindingRepository;
        this.mapService = mapService;
        this.stationService = stationService;
    }

    @Transactional
    public MapVersion createVersion(Long mapId, CreateMapVersionRequest request) {
        MapEntity map = mapService.getById(mapId);
        int nextVersionNo = mapVersionRepository.findByMapId(mapId).stream()
                .mapToInt(MapVersion::getVersionNo)
                .max()
                .orElse(0) + 1;
        MapVersion version = new MapVersion(map, nextVersionNo, request.filePath(), request.coordinateSystem(), null);
        return mapVersionRepository.save(version);
    }

    public MapVersion getById(Long id) {
        return mapVersionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MAP_VERSION_NOT_FOUND", "지도 버전을 찾을 수 없습니다: " + id));
    }

    public List<MapVersion> getByMap(Long mapId) {
        mapService.getById(mapId);
        return mapVersionRepository.findByMapId(mapId).stream()
                .sorted(Comparator.comparing(MapVersion::getVersionNo))
                .toList();
    }

    public MapVersionResponse toResponse(MapVersion version) {
        return MapVersionResponse.from(
                version,
                nodeRepository.countByMapVersionId(version.getId()),
                edgeRepository.countByMapVersionId(version.getId()),
                zoneRepository.countByMapVersionId(version.getId())
        );
    }

    @Transactional
    public Node addNode(Long versionId, CreateNodeRequest request) {
        MapVersion version = getById(versionId);
        requireDraft(version);
        if (nodeRepository.existsByMapVersionIdAndCode(versionId, request.code())) {
            throw new DuplicateResourceException(
                    "NODE_CODE_DUPLICATE", "이미 존재하는 node code입니다: " + request.code()
            );
        }
        return nodeRepository.save(new Node(version, request.code(), request.x(), request.y(), request.nodeType()));
    }

    public List<Node> getNodes(Long versionId) {
        getById(versionId);
        return nodeRepository.findByMapVersionId(versionId);
    }

    @Transactional
    public Edge addEdge(Long versionId, CreateEdgeRequest request) {
        MapVersion version = getById(versionId);
        requireDraft(version);
        Node fromNode = getNodeInVersion(versionId, request.fromNodeId());
        Node toNode = getNodeInVersion(versionId, request.toNodeId());
        return edgeRepository.save(new Edge(version, fromNode, toNode, request.direction(), request.distance(), request.maxCapacity()));
    }

    public List<Edge> getEdges(Long versionId) {
        getById(versionId);
        return edgeRepository.findByMapVersionId(versionId);
    }

    @Transactional
    public Zone addZone(Long versionId, CreateZoneRequest request) {
        MapVersion version = getById(versionId);
        requireDraft(version);
        if (zoneRepository.existsByMapVersionIdAndCode(versionId, request.code())) {
            throw new DuplicateResourceException(
                    "ZONE_CODE_DUPLICATE", "이미 존재하는 zone code입니다: " + request.code()
            );
        }
        Set<Node> nodes = request.nodeIds().stream()
                .map(nodeId -> getNodeInVersion(versionId, nodeId))
                .collect(Collectors.toSet());
        return zoneRepository.save(new Zone(version, request.code(), request.zoneType(), request.maxCapacity(), nodes));
    }

    public List<Zone> getZones(Long versionId) {
        getById(versionId);
        return zoneRepository.findByMapVersionId(versionId);
    }

    @Transactional
    public MapVersionValidationResponse validate(Long versionId) {
        MapVersion version = getById(versionId);
        if (version.getStatus() != MapVersionStatus.DRAFT) {
            throw new InvalidStateTransitionException(
                    "MAP_VERSION_VALIDATE_NOT_ALLOWED",
                    "지도 버전 상태가 DRAFT가 아니어서(현재: " + version.getStatus() + ") 검증할 수 없습니다."
            );
        }
        List<String> errors = collectValidationErrors(versionId);
        if (errors.isEmpty()) {
            version.markValidated();
        }
        return new MapVersionValidationResponse(errors.isEmpty(), errors, toResponse(version));
    }

    /**
     * 최소 검증만 수행한다 — 연결성/고립 노드 등 상세 규칙은 지도 데이터 형식이
     * 정해진 뒤 확정한다 (docs/ERD.md §6).
     */
    private List<String> collectValidationErrors(Long versionId) {
        List<String> errors = new ArrayList<>();
        if (nodeRepository.countByMapVersionId(versionId) == 0) {
            errors.add("노드가 1개 이상 있어야 합니다.");
        }
        return errors;
    }

    @Transactional
    public MapVersion activate(Long versionId) {
        MapVersion version = getById(versionId);
        mapVersionRepository.findByMapIdAndStatus(version.getMap().getId(), MapVersionStatus.ACTIVE)
                .ifPresent(MapVersion::archive);
        version.activate();
        return version;
    }

    @Transactional
    public StationMapBinding bindStation(Long stationId, BindStationMapRequest request) {
        Station station = stationService.getById(stationId);
        MapVersion version = getById(request.mapVersionId());
        if (version.getStatus() != MapVersionStatus.ACTIVE) {
            throw new InvalidStateTransitionException(
                    "MAP_VERSION_NOT_ACTIVE",
                    "지도 버전 상태가 ACTIVE가 아니어서(현재: " + version.getStatus() + ") station을 매핑할 수 없습니다."
            );
        }
        Node node = getNodeInVersion(version.getId(), request.nodeId());
        return stationMapBindingRepository.findByStationIdAndMapVersionId(stationId, version.getId())
                .map(existing -> {
                    existing.rebind(node);
                    return existing;
                })
                .orElseGet(() -> stationMapBindingRepository.save(new StationMapBinding(station, version, node)));
    }

    private Node getNodeInVersion(Long versionId, Long nodeId) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new EntityNotFoundException("NODE_NOT_FOUND", "노드를 찾을 수 없습니다: " + nodeId));
        if (!node.getMapVersion().getId().equals(versionId)) {
            throw new EntityNotFoundException(
                    "NODE_NOT_IN_VERSION", "노드(" + nodeId + ")가 이 지도 버전(" + versionId + ") 소속이 아닙니다."
            );
        }
        return node;
    }

    private void requireDraft(MapVersion version) {
        if (version.getStatus() != MapVersionStatus.DRAFT) {
            throw new InvalidStateTransitionException(
                    "MAP_VERSION_NOT_DRAFT",
                    "지도 버전 상태가 DRAFT가 아니어서(현재: " + version.getStatus() + ") 수정할 수 없습니다."
            );
        }
    }
}
