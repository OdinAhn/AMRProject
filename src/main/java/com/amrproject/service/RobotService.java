package com.amrproject.service;

import com.amrproject.domain.entity.Robot;
import com.amrproject.domain.entity.RobotConfiguration;
import com.amrproject.domain.entity.Site;
import com.amrproject.dto.request.CreateRobotConfigurationRequest;
import com.amrproject.dto.request.CreateRobotRequest;
import com.amrproject.dto.request.UpdateRobotLifecycleStatusRequest;
import com.amrproject.exception.DuplicateResourceException;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.exception.InvalidRequestException;
import com.amrproject.repository.RobotConfigurationRepository;
import com.amrproject.repository.RobotRepository;
import com.amrproject.robot.RobotGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RobotService {

    private final RobotRepository robotRepository;
    private final RobotConfigurationRepository robotConfigurationRepository;
    private final SiteService siteService;
    private final RobotGateway robotGateway;

    public RobotService(
            RobotRepository robotRepository,
            RobotConfigurationRepository robotConfigurationRepository,
            SiteService siteService,
            RobotGateway robotGateway
    ) {
        this.robotRepository = robotRepository;
        this.robotConfigurationRepository = robotConfigurationRepository;
        this.siteService = siteService;
        this.robotGateway = robotGateway;
    }

    @Transactional
    public Robot create(CreateRobotRequest request) {
        Site site = siteService.getById(request.siteId());
        if (robotRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("ROBOT_CODE_DUPLICATE", "이미 존재하는 robot code입니다: " + request.code());
        }
        return robotRepository.save(new Robot(request.code(), request.name(), request.model(), request.manufacturer(), site));
    }

    public Robot getById(Long id) {
        return robotRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ROBOT_NOT_FOUND", "로봇을 찾을 수 없습니다: " + id));
    }

    public List<Robot> getAll(Long siteId) {
        if (siteId != null) {
            siteService.getById(siteId);
            return robotRepository.findBySiteId(siteId);
        }
        return robotRepository.findAll();
    }

    @Transactional
    public Robot updateLifecycleStatus(Long id, UpdateRobotLifecycleStatusRequest request) {
        Robot robot = getById(id);
        robot.changeLifecycleStatus(request.lifecycleStatus());
        return robot;
    }

    /**
     * 새 설정 버전을 만들어 desired로 지정하고, RobotGateway로 적용을 요청한다.
     * 실제 로봇 통신이 없어 시뮬레이터 구현체는 즉시 적용 성공으로 처리한다 (6단계).
     */
    @Transactional
    public RobotConfiguration createConfiguration(Long robotId, CreateRobotConfigurationRequest request) {
        if (request.chargingTargetPct() <= request.chargingLowThresholdPct()) {
            throw new InvalidRequestException(
                    "CHARGING_TARGET_MUST_EXCEED_LOW_THRESHOLD",
                    "충전 목표(%)는 저배터리 임계값(%)보다 커야 합니다."
            );
        }
        Robot robot = getById(robotId);
        int nextVersionNo = robotConfigurationRepository.countByRobotId(robotId) + 1;
        RobotConfiguration configuration = new RobotConfiguration(
                robot,
                nextVersionNo,
                request.maxPayloadKg(),
                request.maxSpeedMps(),
                request.batteryCapacityWh(),
                request.chargingLowThresholdPct(),
                request.chargingTargetPct(),
                null
        );
        robotConfigurationRepository.save(configuration);
        robot.assignDesiredConfiguration(configuration);
        robotGateway.applyConfiguration(configuration);
        return configuration;
    }

    public List<RobotConfiguration> getConfigurations(Long robotId) {
        getById(robotId);
        return robotConfigurationRepository.findByRobotIdOrderByVersionNoAsc(robotId);
    }
}
