package com.amrproject.controller.site;

import com.amrproject.dto.request.site.BindStationMapRequest;
import com.amrproject.dto.request.site.CreateStationRequest;
import com.amrproject.dto.request.site.UpdateStationRequest;
import com.amrproject.dto.response.site.StationMapBindingResponse;
import com.amrproject.dto.response.site.StationResponse;
import com.amrproject.service.map.MapVersionService;
import com.amrproject.service.site.StationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StationController {

    private final StationService stationService;
    private final MapVersionService mapVersionService;

    public StationController(StationService stationService, MapVersionService mapVersionService) {
        this.stationService = stationService;
        this.mapVersionService = mapVersionService;
    }

    @GetMapping("/api/admin/sites/{siteId}/stations")
    public List<StationResponse> getBySite(@PathVariable Long siteId) {
        return stationService.getBySite(siteId).stream().map(StationResponse::from).toList();
    }

    @PostMapping("/api/admin/sites/{siteId}/stations")
    public ResponseEntity<StationResponse> create(@PathVariable Long siteId, @Valid @RequestBody CreateStationRequest request) {
        StationResponse response = StationResponse.from(stationService.create(siteId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/admin/stations/{stationId}")
    public StationResponse getById(@PathVariable Long stationId) {
        return StationResponse.from(stationService.getById(stationId));
    }

    @PatchMapping("/api/admin/stations/{stationId}")
    public StationResponse update(@PathVariable Long stationId, @RequestBody UpdateStationRequest request) {
        return StationResponse.from(stationService.update(stationId, request));
    }

    @PutMapping("/api/admin/stations/{stationId}/map-binding")
    public StationMapBindingResponse bindMap(@PathVariable Long stationId, @Valid @RequestBody BindStationMapRequest request) {
        return StationMapBindingResponse.from(mapVersionService.bindStation(stationId, request));
    }
}
