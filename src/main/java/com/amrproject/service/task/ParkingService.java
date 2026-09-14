package com.amrproject.service.task;

import com.amrproject.domain.entity.robot.CommandEvent;
import com.amrproject.domain.entity.robot.Robot;
import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotCommandType;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.domain.entity.task.TaskAssignmentStatus;
import com.amrproject.repository.robot.CommandEventRepository;
import com.amrproject.repository.robot.RobotCommandRepository;
import com.amrproject.repository.robot.RobotRepository;
import com.amrproject.repository.task.TaskAssignmentRepository;
import com.amrproject.robot.RobotGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 48단계: 할 일 없는 로봇을 대기 구역(지도 구역 유형 {@code PARKING} — 예: 선반이 모여 있는 곳 아래)으로 보낸다.
 *
 * <p>디스패치 사이클이 작업 배정·충전 다음에 로봇마다 부른다. 보내는 조건: 운영 중, 진행 중 작업 없음, 마지막 작업이 끝난 뒤
 * {@code app.parking.idle-seconds}가 지남, 배터리가 충전 기준보다 높음(낮으면 같은 사이클의 충전이 맡는다), 움직일 수 있는 상태,
 * 아직 대기 구역에 있지 않음. 어느 구역·노드로 갈지는 {@link TrafficService#planParking}이 정한다(구역별로 고르게).
 *
 * <p>이동은 비키기(47단계)와 같은 작업 없는 {@code REPOSITION} 명령이고(명령 이벤트 {@code PARKING}), 경로 예약·결과 반영·타임아웃도 같다.
 * 잠금 순서: 로봇 행 → 배정 행 → (계획 중) 물류센터 행 — 배정(작업 → 로봇 → 배정 → 물류센터)과 같은 방향이다.
 */
@Service
public class ParkingService {

    private final boolean enabled;
    private final long idleSeconds;
    private final RobotRepository robotRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final RobotCommandRepository robotCommandRepository;
    private final CommandEventRepository commandEventRepository;
    private final TrafficService trafficService;
    private final RobotGateway robotGateway;

    public ParkingService(
            @Value("${app.parking.enabled:true}") boolean enabled,
            @Value("${app.parking.idle-seconds:30}") long idleSeconds,
            RobotRepository robotRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            RobotCommandRepository robotCommandRepository,
            CommandEventRepository commandEventRepository,
            TrafficService trafficService,
            RobotGateway robotGateway
    ) {
        this.enabled = enabled;
        this.idleSeconds = idleSeconds;
        this.robotRepository = robotRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.robotCommandRepository = robotCommandRepository;
        this.commandEventRepository = commandEventRepository;
        this.trafficService = trafficService;
        this.robotGateway = robotGateway;
    }

    /**
     * 조건을 만족하면 로봇을 대기 구역으로 보내고 true.
     *
     * @param lowBatteryPct 이 값 이하 배터리면 보내지 않는다(충전 대상)
     */
    @Transactional
    public boolean parkIfIdle(Long robotId, int lowBatteryPct) {
        if (!enabled) {
            return false;
        }
        Robot robot = robotRepository.findByIdForUpdate(robotId).orElse(null);
        if (robot == null || robot.getLifecycleStatus() != RobotLifecycleStatus.IN_SERVICE
                || taskAssignmentRepository.findByRobotIdAndStatusForUpdate(robotId, TaskAssignmentStatus.ACTIVE).isPresent()) {
            return false;
        }
        boolean recentlyBusy = taskAssignmentRepository.findLastReleasedAt(robotId)
                .map(releasedAt -> releasedAt.isAfter(Instant.now().minusSeconds(idleSeconds)))
                .orElse(false);
        if (recentlyBusy) {
            return false;
        }

        TrafficService.Plan plan = trafficService.planParking(robot, lowBatteryPct);
        if (plan.outcome() != TrafficService.Outcome.CLEAR) {
            return false;
        }
        RobotCommand command = robotCommandRepository.save(new RobotCommand(
                UUID.randomUUID().toString(), null, robot, RobotCommandType.REPOSITION, plan.payloadJson()));
        Instant now = Instant.now();
        commandEventRepository.save(new CommandEvent(command, "PARKING", null, now, now));
        robotGateway.dispatch(command);
        trafficService.releaseNodesIfRepositionFinished(command);
        return true;
    }
}
