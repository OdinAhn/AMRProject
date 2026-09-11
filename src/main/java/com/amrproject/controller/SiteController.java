package com.amrproject.controller;

import com.amrproject.dto.request.CreateSiteRequest;
import com.amrproject.dto.request.UpdateSiteRequest;
import com.amrproject.dto.response.SiteResponse;
import com.amrproject.service.SiteService;
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

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
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
