package com.amrproject.controller.map;

import com.amrproject.dto.request.map.CreateEdgeRequest;
import com.amrproject.dto.request.map.CreateMapRequest;
import com.amrproject.dto.request.map.CreateMapVersionRequest;
import com.amrproject.dto.request.map.CreateNodeRequest;
import com.amrproject.dto.request.map.CreateObstacleRequest;
import com.amrproject.dto.request.map.CreateZoneRequest;
import com.amrproject.dto.response.map.EdgeResponse;
import com.amrproject.dto.response.map.MapResponse;
import com.amrproject.dto.response.map.MapVersionResponse;
import com.amrproject.dto.response.map.MapVersionValidationResponse;
import com.amrproject.dto.response.map.NodeResponse;
import com.amrproject.dto.response.map.ObstacleResponse;
import com.amrproject.dto.response.map.ZoneResponse;
import com.amrproject.service.map.MapService;
import com.amrproject.service.map.MapVersionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 지도 API (docs/API.md §2.3). 노드/통로/구역 생성 엔드포인트는 지도 파일
 * 업로드·파싱 대신 임시로 둔 저작 도구다 (지도 파일 형식 미정, docs/DESIGN.md).
 */
@RestController
public class MapController {

    private final MapService mapService;
    private final MapVersionService mapVersionService;

    public MapController(MapService mapService, MapVersionService mapVersionService) {
        this.mapService = mapService;
        this.mapVersionService = mapVersionService;
    }

    @GetMapping("/api/admin/sites/{siteId}/maps")
    public List<MapResponse> getBySite(@PathVariable Long siteId) {
        return mapService.getBySite(siteId).stream().map(MapResponse::from).toList();
    }

    @PostMapping("/api/admin/sites/{siteId}/maps")
    public ResponseEntity<MapResponse> createMap(@PathVariable Long siteId, @Valid @RequestBody CreateMapRequest request) {
        MapResponse response = MapResponse.from(mapService.create(siteId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/admin/maps/{mapId}/versions")
    public ResponseEntity<MapVersionResponse> createVersion(@PathVariable Long mapId, @RequestBody CreateMapVersionRequest request) {
        MapVersionResponse response = mapVersionService.toResponse(mapVersionService.createVersion(mapId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/admin/maps/{mapId}/versions")
    public List<MapVersionResponse> getVersions(@PathVariable Long mapId) {
        return mapVersionService.getByMap(mapId).stream().map(mapVersionService::toResponse).toList();
    }

    @GetMapping("/api/admin/map-versions/{versionId}")
    public MapVersionResponse getVersion(@PathVariable Long versionId) {
        return mapVersionService.toResponse(mapVersionService.getById(versionId));
    }

    @PostMapping("/api/admin/map-versions/{versionId}/nodes")
    public ResponseEntity<NodeResponse> addNode(@PathVariable Long versionId, @Valid @RequestBody CreateNodeRequest request) {
        NodeResponse response = NodeResponse.from(mapVersionService.addNode(versionId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/admin/map-versions/{versionId}/nodes")
    public List<NodeResponse> getNodes(@PathVariable Long versionId) {
        return mapVersionService.getNodes(versionId).stream().map(NodeResponse::from).toList();
    }

    @PostMapping("/api/admin/map-versions/{versionId}/edges")
    public ResponseEntity<EdgeResponse> addEdge(@PathVariable Long versionId, @Valid @RequestBody CreateEdgeRequest request) {
        EdgeResponse response = EdgeResponse.from(mapVersionService.addEdge(versionId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/admin/map-versions/{versionId}/edges")
    public List<EdgeResponse> getEdges(@PathVariable Long versionId) {
        return mapVersionService.getEdges(versionId).stream().map(EdgeResponse::from).toList();
    }

    @PostMapping("/api/admin/map-versions/{versionId}/zones")
    public ResponseEntity<ZoneResponse> addZone(@PathVariable Long versionId, @Valid @RequestBody CreateZoneRequest request) {
        ZoneResponse response = ZoneResponse.from(mapVersionService.addZone(versionId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/admin/map-versions/{versionId}/zones")
    public List<ZoneResponse> getZones(@PathVariable Long versionId) {
        return mapVersionService.getZones(versionId).stream().map(ZoneResponse::from).toList();
    }

    @PostMapping("/api/admin/map-versions/{versionId}/obstacles")
    public ResponseEntity<ObstacleResponse> addObstacle(@PathVariable Long versionId, @Valid @RequestBody CreateObstacleRequest request) {
        ObstacleResponse response = ObstacleResponse.from(mapVersionService.addObstacle(versionId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/admin/map-versions/{versionId}/obstacles")
    public List<ObstacleResponse> getObstacles(@PathVariable Long versionId) {
        return mapVersionService.getObstacles(versionId).stream().map(ObstacleResponse::from).toList();
    }

    @PostMapping("/api/admin/map-versions/{versionId}/validate")
    public MapVersionValidationResponse validate(@PathVariable Long versionId) {
        return mapVersionService.validate(versionId);
    }

    @PostMapping("/api/admin/map-versions/{versionId}/activate")
    public MapVersionResponse activate(@PathVariable Long versionId) {
        return mapVersionService.toResponse(mapVersionService.activate(versionId));
    }
}
