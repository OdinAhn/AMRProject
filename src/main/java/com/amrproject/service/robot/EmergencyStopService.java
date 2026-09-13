package com.amrproject.service.robot;

import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotCommandType;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.repository.robot.RobotCommandRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.robot.RobotGateway;
import com.amrproject.robot.SimulatorRobotGateway;
import com.amrproject.service.site.SiteService;
import com.amrproject.service.task.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 33단계: 비상정지. 로봇에 EMERGENCY_STOP 명령을 보내고, 그 로봇이 맡은 작업을 복구 필요로 보낸다.
 * 명령이 전달되지 않았어도(UNKNOWN) 작업은 멈춘 것으로 다룬다 — 운영자가 정지를 원했으므로 자동으로 이어가면 안 된다.
 * 로봇이 실제로 멈췄는지는 로봇이 보고하는 {@code safety_state}로 판단하고, 해제도 로봇(현장)이 NORMAL을 보고해야 된다.
 */
@Service
@Transactional(readOnly = true)
public class EmergencyStopService {

    private final RobotRepository robotRepository;
    private final RobotCommandRepository robotCommandRepository;
    private final RobotGateway robotGateway;
    private final RobotService robotService;
    private final SiteService siteService;
    private final TaskService taskService;
    private final SimulatorRobotGateway simulatorRobotGateway;

    public EmergencyStopService(
            RobotRepository robotRepository,
            RobotCommandRepository robotCommandRepository,
            RobotGateway robotGateway,
            RobotService robotService,
            SiteService siteService,
            TaskService taskService,
            SimulatorRobotGateway simulatorRobotGateway
    ) {
        this.robotRepository = robotRepository;
        this.robotCommandRepository = robotCommandRepository;
        this.robotGateway = robotGateway;
        this.robotService = robotService;
        this.siteService = siteService;
        this.taskService = taskService;
        this.simulatorRobotGateway = simulatorRobotGateway;
    }

    @Transactional
    public RobotCommand stopRobot(Long robotId) {
        return stop(robotService.getById(robotId));
    }

    /** 물류센터의 운영 중(IN_SERVICE) 로봇을 모두 비상정지한다. */
    @Transactional
    public List<RobotCommand> stopSite(Long siteId) {
        siteService.getById(siteId);
        return robotRepository.findBySiteId(siteId).stream()
                .filter(robot -> robot.getLifecycleStatus() == RobotLifecycleStatus.IN_SERVICE)
                .map(this::stop)
                .toList();
    }

    /** 시뮬레이터 전용 — 현장에서 비상정지를 해제한 것을 흉내 낸다. */
    @Transactional
    public void releaseSimulatedEmergencyStop(Long robotId) {
        simulatorRobotGateway.releaseEmergencyStop(robotService.getById(robotId));
    }

    private RobotCommand stop(Robot robot) {
        RobotCommand command = robotCommandRepository.save(
                new RobotCommand(UUID.randomUUID().toString(), null, robot, RobotCommandType.EMERGENCY_STOP, null)
        );
        robotGateway.dispatch(command);
        taskService.interruptByEmergencyStop(robot);
        return command;
    }
}
