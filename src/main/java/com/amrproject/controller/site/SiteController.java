package com.amrproject.controller.site;

import com.amrproject.dto.request.site.CreateSiteRequest;
import com.amrproject.dto.request.site.UpdateSiteRequest;
import com.amrproject.dto.response.site.SiteOverviewResponse;
import com.amrproject.dto.response.site.SiteResponse;
import com.amrproject.service.site.SiteOverviewService;
import com.amrproject.service.site.SiteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sites")
public class SiteController {

    private final SiteService siteService;
    private final SiteOverviewService siteOverviewService;

    public SiteController(SiteService siteService, SiteOverviewService siteOverviewService) {
        this.siteService = siteService;
        this.siteOverviewService = siteOverviewService;
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
