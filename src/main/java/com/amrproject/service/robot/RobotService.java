package com.amrproject.service.robot;

import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.LoadState;
import com.amrproject.domain.entity.robot.LocalizationState;
import com.amrproject.domain.entity.robot.MotionState;
import com.amrproject.domain.entity.robot.OperationMode;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotConfiguration;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.domain.entity.robot.SafetyState;
import com.amrproject.domain.entity.site.Site;
import com.amrproject.dto.request.robot.CreateRobotConfigurationRequest;
import com.amrproject.dto.request.robot.CreateRobotRequest;
import com.amrproject.dto.request.robot.UpdateRobotLifecycleStatusRequest;
import com.amrproject.dto.request.robot.UpdateRobotRequest;
import com.amrproject.exception.DuplicateResourceException;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.exception.InvalidRequestException;
import com.amrproject.repository.robot.RobotConfigurationRepository;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.robot.RobotGateway;
import com.amrproject.service.site.SiteService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RobotService {

    private final RobotRepository robotRepository;
    private final RobotConfigurationRepository robotConfigurationRepository;
    private final RobotCurrentStateRepository robotCurrentStateRepository;
    private final SiteService siteService;
    private final RobotGateway robotGateway;

    public RobotService(
            RobotRepository robotRepository,
            RobotConfigurationRepository robotConfigurationRepository,
            RobotCurrentStateRepository robotCurrentStateRepository,
            SiteService siteService,
            RobotGateway robotGateway
    ) {
        this.robotRepository = robotRepository;
        this.robotConfigurationRepository = robotConfigurationRepository;
        this.robotCurrentStateRepository = robotCurrentStateRepository;
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

    /** 로봇 기본정보(이름/모델/제조사) 수정 — code/site는 등록 후 변경하지 않는다 (17단계). */
    @Transactional
    public Robot update(Long id, UpdateRobotRequest request) {
        Robot robot = getById(id);
        String name = request.name() != null ? request.name() : robot.getName();
        String model = request.model() != null ? request.model() : robot.getModel();
        String manufacturer = request.manufacturer() != null ? request.manufacturer() : robot.getManufacturer();
        robot.updateBasicInfo(name, model, manufacturer);
        return robot;
    }

    /**
     * IN_SERVICE로 전환하는 로봇에 아직 보고된 상태가 없으면 온라인 기본값으로
     * 초기화한다 — 그렇지 않으면 한 번도 명령을 받아본 적 없는 새 로봇은
     * DispatchService의 배정 대상 조건(온라인/자동/위치추정됨)을 절대 만족할 수 없다
     * (지금까지는 SimulatorRobotGateway가 첫 명령을 보낼 때만 상태를 만들었다).
     */
    @Transactional
    public Robot updateLifecycleStatus(Long id, UpdateRobotLifecycleStatusRequest request) {
        Robot robot = getById(id);
        robot.changeLifecycleStatus(request.lifecycleStatus());
        if (request.lifecycleStatus() == RobotLifecycleStatus.IN_SERVICE
                && robotCurrentStateRepository.findById(robot.getId()).isEmpty()) {
            RobotCurrentState state = new RobotCurrentState(robot);
            state.report(
                    ConnectionState.ONLINE, OperationMode.AUTO, MotionState.IDLE, SafetyState.NORMAL,
                    LoadState.EMPTY, LocalizationState.LOCALIZED, 100, Instant.now()
            );
            robotCurrentStateRepository.save(state);
        }
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
