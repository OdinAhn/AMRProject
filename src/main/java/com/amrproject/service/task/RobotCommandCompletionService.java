package com.amrproject.service.task;

import com.amrproject.domain.entity.robot.CommandEvent;
import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotCommandStatus;
import com.amrproject.domain.entity.robot.RobotCommandType;
import com.amrproject.repository.robot.CommandEventRepository;
import com.amrproject.repository.robot.RobotCommandRepository;
import com.amrproject.repository.task.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 39단계: 비동기 로봇 명령(MQTT)의 결과·타임아웃을 반영하는 단일 진입점.
 *
 * <p>예전엔 게이트웨이가 요청 트랜잭션 안에서(작업·로봇·작업장 행 잠금을 쥔 채) 결과를 최대 3초 기다렸다. 이제 요청은 명령을 기록하고
 * 끝나며, 결과는 MQTT 콜백·타임아웃 감시가 이 서비스를 통해 <b>별도 트랜잭션</b>으로 반영한다.
 *
 * <p>잠금 순서: 작업 행 → 명령 행 → 로봇 상태 행. 작업 API(TaskService)가 작업 행을 먼저 잠그는 순서와 맞춰 교착을 피한다.
 * 이미 최종 상태인 명령(중복 메시지, 타임아웃 뒤 늦게 온 결과가 두 번 등)은 건드리지 않는다.
 */
@Service
public class RobotCommandCompletionService {

    /** 결과를 기다리는 중인 명령 상태 */
    private static final List<RobotCommandStatus> IN_FLIGHT = List.of(
            RobotCommandStatus.SENT, RobotCommandStatus.ACCEPTED, RobotCommandStatus.RUNNING
    );
    /** 타임아웃 감시 대상 — 비상정지는 전달(ACCEPTED)까지만 추적하는 명령이라 제외한다(33단계) */
    private static final List<RobotCommandType> AWAITED_TYPES = List.of(RobotCommandType.MOVE_TO, RobotCommandType.START_CHARGE);

    private final RobotCommandRepository robotCommandRepository;
    private final CommandEventRepository commandEventRepository;
    private final TaskRepository taskRepository;
    private final TaskService taskService;

    public RobotCommandCompletionService(
            RobotCommandRepository robotCommandRepository,
            CommandEventRepository commandEventRepository,
            TaskRepository taskRepository,
            TaskService taskService
    ) {
        this.robotCommandRepository = robotCommandRepository;
        this.commandEventRepository = commandEventRepository;
        this.taskRepository = taskRepository;
        this.taskService = taskService;
    }

    /**
     * 로봇이 명령을 끝냈다고(성공/실패) 알려 왔다. robotStateUpdater는 게이트웨이가 로봇 현재 상태(정지·배터리)를 반영하는 콜백으로,
     * 명령 행을 잠근 뒤 같은 트랜잭션에서 부른다. 모르는 commandNo면 무시한다(다른 서버·수동 주문 등).
     */
    @Transactional
    public void complete(String commandNo, boolean succeeded, Consumer<RobotCommand> robotStateUpdater) {
        robotCommandRepository.findIdByCommandNo(commandNo).ifPresent(commandId -> {
            RobotCommand command = lockInOrder(commandId);
            RobotCommandStatus status = command.getStatus();
            if (status == RobotCommandStatus.SUCCEEDED || status == RobotCommandStatus.FAILED) {
                return;
            }
            // 발행 확인(ACCEPTED/RUNNING 기록)보다 결과가 먼저 도착할 수 있다 — 빠진 단계를 채운 뒤 끝낸다
            if (status == RobotCommandStatus.SENT) {
                command.accept();
            }
            if (command.getStatus() == RobotCommandStatus.ACCEPTED) {
                command.startRunning();
            }
            if (succeeded) {
                command.succeed();
            } else {
                command.fail();
            }
            recordEvent(command, succeeded ? "SUCCEEDED" : "FAILED");
            robotStateUpdater.accept(command);
            // 40단계: 결과 불명(UNKNOWN)이던 명령의 늦은 성공이면 작업을 자동으로 이어갈 수 있다 — 직전 상태를 함께 넘긴다
            taskService.applyCommandOutcome(command, status);
        });
    }

    /**
     * 결과가 오지 않았거나(타임아웃) 보내지 못했다 — 실제로 움직였는지 알 수 없으므로 UNKNOWN으로 두고 작업은 복구 필요로 보낸다
     * (STATE_MACHINES.md §3: 임의로 실패로 간주해 재전송하지 않음). 그 사이 결과가 도착해 최종 상태면 아무것도 하지 않는다.
     */
    @Transactional
    public void markUnknown(Long commandId, String eventType, Consumer<RobotCommand> robotStateUpdater) {
        lockInOrderIfExists(commandId).ifPresent(command -> {
            if (!IN_FLIGHT.contains(command.getStatus())) {
                return;
            }
            if (command.getStatus() == RobotCommandStatus.ACCEPTED) {
                command.startRunning();
            }
            RobotCommandStatus previous = command.getStatus();
            command.markUnknown();
            recordEvent(command, eventType);
            robotStateUpdater.accept(command);
            taskService.applyCommandOutcome(command, previous);
        });
    }

    /** 보낸 뒤 timeoutSeconds가 지나도록 결과가 없는 이동·충전 명령 id. */
    @Transactional(readOnly = true)
    public List<Long> findTimedOut(long timeoutSeconds) {
        return robotCommandRepository.findIdsInFlightSentBefore(IN_FLIGHT, AWAITED_TYPES, Instant.now().minusSeconds(timeoutSeconds));
    }

    private RobotCommand lockInOrder(Long commandId) {
        return lockInOrderIfExists(commandId).orElseThrow();
    }

    /** 작업 행을 먼저 잠그고 명령 행을 잠근다. 두 엔티티 모두 잠금 조회로 처음 읽어 최신 커밋 값을 본다. */
    private Optional<RobotCommand> lockInOrderIfExists(Long commandId) {
        robotCommandRepository.findTaskIdByCommandId(commandId).ifPresent(taskRepository::findByIdForUpdate);
        return robotCommandRepository.findByIdForUpdate(commandId);
    }

    private void recordEvent(RobotCommand command, String eventType) {
        Instant now = Instant.now();
        commandEventRepository.save(new CommandEvent(command, eventType, null, now, now));
    }
}
