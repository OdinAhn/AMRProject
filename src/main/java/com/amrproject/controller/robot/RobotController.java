package com.amrproject.controller.robot;

import com.amrproject.dto.request.robot.CreateRobotConfigurationRequest;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.robot.InjectFaultRequest;
import com.amrproject.dto.request.robot.UpdateRobotLifecycleStatusRequest;
import com.amrproject.dto.request.robot.UpdateRobotRequest;
import com.amrproject.dto.response.robot.EmergencyStopResponse;
import com.amrproject.dto.response.robot.RobotConfigurationResponse;
import com.amrproject.dto.response.robot.RobotResponse;
import com.amrproject.dto.response.robot.RobotStateResponse;
import com.amrproject.service.robot.EmergencyStopService;
import com.amrproject.service.robot.RobotService;
import com.amrproject.service.robot.RobotStateService;
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
    private final RobotStateService robotStateService;
    private final EmergencyStopService emergencyStopService;

    public RobotController(RobotService robotService, RobotStateService robotStateService, EmergencyStopService emergencyStopService) {
        this.robotService = robotService;
        this.robotStateService = robotStateService;
        this.emergencyStopService = emergencyStopService;
    }

    @GetMapping
    public List<RobotResponse> getAll(@RequestParam(required = false) Long siteId) {
        return robotService.getAll(siteId).stream()
                .map(robot -> RobotResponse.from(robot, robotStateService.findCurrentState(robot.getId()).orElse(null)))
                .toList();
    }

    @PostMapping
    public ResponseEntity<RobotResponse> create(@Valid @RequestBody CreateRobotRequest request) {
        RobotResponse response = RobotResponse.from(robotService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{robotId}")
    public RobotResponse getById(@PathVariable Long robotId) {
        return RobotResponse.from(robotService.getById(robotId), robotStateService.findCurrentState(robotId).orElse(null));
    }

    @PatchMapping("/{robotId}")
    public RobotResponse update(@PathVariable Long robotId, @RequestBody UpdateRobotRequest request) {
        return RobotResponse.from(robotService.update(robotId, request));
    }

    @PatchMapping("/{robotId}/lifecycle-status")
    public RobotResponse updateLifecycleStatus(
            @PathVariable Long robotId,
            @Valid @RequestBody UpdateRobotLifecycleStatusRequest request
    ) {
        return RobotResponse.from(robotService.updateLifecycleStatus(robotId, request));
    }

    @GetMapping("/{robotId}/state")
    public RobotStateResponse getState(@PathVariable Long robotId) {
        return RobotStateResponse.from(robotStateService.getCurrentState(robotId));
    }

    @GetMapping("/{robotId}/state-history")
    public List<RobotStateResponse> getStateHistory(
            @PathVariable Long robotId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return robotStateService.getStateHistory(robotId, limit).stream().map(RobotStateResponse::from).toList();
    }

    @PostMapping("/{robotId}/configurations")
    public ResponseEntity<RobotConfigurationResponse> createConfiguration(
            @PathVariable Long robotId,
            @Valid @RequestBody CreateRobotConfigurationRequest request
    ) {
        RobotConfigurationResponse response = RobotConfigurationResponse.from(robotService.createConfiguration(robotId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{robotId}/configurations")
    public List<RobotConfigurationResponse> getConfigurations(@PathVariable Long robotId) {
        return robotService.getConfigurations(robotId).stream().map(RobotConfigurationResponse::from).toList();
    }

    /**
     * 시뮬레이터 전용 운영 도구 — 실제 제품 기능이 아니다 (docs/API.md §7).
     * 해당 로봇에 대한 다음 명령 디스패치 한 번에만 적용된다.
     */
    /** 33단계: 로봇 비상정지. 응답의 명령 상태는 전달 여부일 뿐 — 실제 정지는 state의 safetyState로 확인한다. */
    @PostMapping("/{robotId}/emergency-stop")
    public EmergencyStopResponse emergencyStop(@PathVariable Long robotId) {
        return EmergencyStopResponse.from(emergencyStopService.stopRobot(robotId));
    }

    /** 33단계(시뮬레이터 전용): 현장 비상정지 해제를 흉내 낸다. 실제 로봇은 원격 해제가 불가능하다고 가정한다. */
    @PostMapping("/{robotId}/simulator/emergency-stop-release")
    public ResponseEntity<Void> releaseSimulatedEmergencyStop(@PathVariable Long robotId) {
        emergencyStopService.releaseSimulatedEmergencyStop(robotId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{robotId}/simulator/faults")
    public ResponseEntity<Void> injectFault(@PathVariable Long robotId, @Valid @RequestBody InjectFaultRequest request) {
        robotStateService.injectFault(robotId, request.type());
        return ResponseEntity.noContent().build();
    }
}
