package com.amrproject.controller;

import com.amrproject.dto.request.CreateRobotRequest;
import com.amrproject.dto.request.UpdateRobotLifecycleStatusRequest;
import com.amrproject.dto.response.RobotResponse;
import com.amrproject.service.RobotService;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/api/admin/robots")
public class RobotController {

    private final RobotService robotService;

    public RobotController(RobotService robotService) {
        this.robotService = robotService;
    }

    @GetMapping
    public List<RobotResponse> getAll(@RequestParam(required = false) Long siteId) {
        return robotService.getAll(siteId).stream().map(RobotResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<RobotResponse> create(@Valid @RequestBody CreateRobotRequest request) {
        RobotResponse response = RobotResponse.from(robotService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{robotId}")
    public RobotResponse getById(@PathVariable Long robotId) {
        return RobotResponse.from(robotService.getById(robotId));
    }

    @PatchMapping("/{robotId}/lifecycle-status")
    public RobotResponse updateLifecycleStatus(
            @PathVariable Long robotId,
            @Valid @RequestBody UpdateRobotLifecycleStatusRequest request
    ) {
        return RobotResponse.from(robotService.updateLifecycleStatus(robotId, request));
    }
}
