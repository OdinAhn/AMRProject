package com.amrproject.controller.site;

import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.UpdateSiteRequest;
import com.amrproject.dto.response.robot.EmergencyStopResponse;
import com.amrproject.dto.response.site.SiteOverviewResponse;
import com.amrproject.dto.response.site.SiteResponse;
import com.amrproject.dto.response.site.SiteStatisticsResponse;
import com.amrproject.service.robot.EmergencyStopService;
import com.amrproject.service.site.SiteOverviewService;
import com.amrproject.service.site.SiteService;
import com.amrproject.service.site.SiteStatisticsService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/sites")
public class SiteController {

    private final SiteService siteService;
    private final SiteOverviewService siteOverviewService;
    private final EmergencyStopService emergencyStopService;
    private final SiteStatisticsService siteStatisticsService;

    public SiteController(
            SiteService siteService,
            SiteOverviewService siteOverviewService,
            EmergencyStopService emergencyStopService,
            SiteStatisticsService siteStatisticsService
    ) {
        this.siteService = siteService;
        this.siteOverviewService = siteOverviewService;
        this.emergencyStopService = emergencyStopService;
        this.siteStatisticsService = siteStatisticsService;
    }

    /** 34단계: 운영 통계 — 기간(zone 기준 날짜, 기본 최근 7일)의 처리량·성공률·평균 시간·로봇 가동률·알람. */
    @GetMapping("/{siteId}/statistics")
    public SiteStatisticsResponse getStatistics(
            @PathVariable Long siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "Asia/Seoul") String zone
    ) {
        return siteStatisticsService.getStatistics(siteId, from, to, zone);
    }

    /** 33단계: 물류센터 전체 비상정지 — 운영 중인 로봇을 모두 멈추고 각 로봇의 작업을 복구 필요로 보낸다. */
    @PostMapping("/{siteId}/emergency-stop")
    public List<EmergencyStopResponse> emergencyStop(@PathVariable Long siteId) {
        return emergencyStopService.stopSite(siteId).stream().map(EmergencyStopResponse::from).toList();
    }

    /** 30단계: 관제 대시보드 — 로봇 상태·위치, 진행 중 작업 수, 열린 알람 수를 한 번에 조회한다. */
    @GetMapping("/{siteId}/overview")
    public SiteOverviewResponse getOverview(@PathVariable Long siteId) {
        return siteOverviewService.getOverview(siteId);
    }

    @GetMapping
    public List<SiteResponse> getAll() {
        return siteService.getAll().stream().map(SiteResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<SiteResponse> create(@Valid @RequestBody CreateSiteRequest request) {
        SiteResponse response = SiteResponse.from(siteService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{siteId}")
    public SiteResponse getById(@PathVariable Long siteId) {
        return SiteResponse.from(siteService.getById(siteId));
    }

    @PatchMapping("/{siteId}")
    public SiteResponse update(@PathVariable Long siteId, @RequestBody UpdateSiteRequest request) {
        return SiteResponse.from(siteService.update(siteId, request));
    }
}
