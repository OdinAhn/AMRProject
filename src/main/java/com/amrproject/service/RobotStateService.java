package com.amrproject.service;

import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.robot.RobotStateSnapshot;
import com.amrproject.exception.EntityNotFoundException;
import com.amrproject.repository.robot.RobotCurrentStateRepository;
import com.amrproject.repository.robot.RobotStateSnapshotRepository;
import com.amrproject.robot.SimulatedFault;
import com.amrproject.robot.SimulatorRobotGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RobotStateService {

    private final RobotCurrentStateRepository robotCurrentStateRepository;
    private final RobotStateSnapshotRepository robotStateSnapshotRepository;
    private final RobotService robotService;
    private final SimulatorRobotGateway simulatorRobotGateway;

    public RobotStateService(
            RobotCurrentStateRepository robotCurrentStateRepository,
            RobotStateSnapshotRepository robotStateSnapshotRepository,
            RobotService robotService,
            SimulatorRobotGateway simulatorRobotGateway
    ) {
        this.robotCurrentStateRepository = robotCurrentStateRepository;
        this.robotStateSnapshotRepository = robotStateSnapshotRepository;
        this.robotService = robotService;
        this.simulatorRobotGateway = simulatorRobotGateway;
    }

    public RobotCurrentState getCurrentState(Long robotId) {
        return robotCurrentStateRepository.findById(robotId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "ROBOT_STATE_NOT_FOUND",
                        "아직 보고된 상태가 없는 로봇입니다: " + robotId
                ));
    }

    public List<RobotStateSnapshot> getStateHistory(Long robotId) {
        robotService.getById(robotId);
        return robotStateSnapshotRepository.findByRobotIdOrderByReportedAtDesc(robotId);
    }

    public void injectFault(Long robotId, SimulatedFault fault) {
        robotService.getById(robotId);
        simulatorRobotGateway.injectFault(robotId, fault);
    }
}
