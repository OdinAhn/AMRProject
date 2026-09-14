package com.amrproject.service.task;

import com.amrproject.repository.robot.RobotCommandRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 46단계: 교통 대기 중인 이동 명령을 다시 계획한다(먼저 기다린 명령부터). 명령마다 별도 트랜잭션이라 하나가 실패해도 나머지는 진행한다.
 * <ul>
 *   <li>경로 예약이 풀리면(이동 종료) 그 트랜잭션이 커밋된 직후 별도 스레드에서 바로 — 기다리던 로봇이 새로 들어온 이동보다 먼저 빈 길을 잡는다.</li>
 *   <li>그 밖의 변화(로봇이 서 있던 자리를 비킴 등)를 위해 주기적으로도. 첫 주기 실행은 한 주기 뒤다.</li>
 * </ul>
 * 두 경로가 동시에 돌아도 같은 명령은 작업·명령 행 잠금으로 한 번만 보내진다.
 */
@Component
@ConditionalOnProperty(name = "app.traffic.enabled", havingValue = "true", matchIfMissing = true)
public class TrafficRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrafficRetryScheduler.class);

    private final RobotCommandRepository robotCommandRepository;
    private final TaskService taskService;
    private final ExecutorService releasedExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "traffic-retry");
        thread.setDaemon(true);
        return thread;
    });

    public TrafficRetryScheduler(RobotCommandRepository robotCommandRepository, TaskService taskService) {
        this.robotCommandRepository = robotCommandRepository;
        this.taskService = taskService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNodesReleased(TrafficService.NodesReleased event) {
        releasedExecutor.execute(this::retryWaiting);
    }

    @PreDestroy
    void shutdown() {
        releasedExecutor.shutdown();
    }

    @Scheduled(fixedDelayString = "${app.traffic.retry-ms:2000}", initialDelayString = "${app.traffic.retry-ms:2000}")
    public void retryWaiting() {
        for (Long commandId : robotCommandRepository.findWaitingMoveCommandIds()) {
            try {
                taskService.retryWaitingCommand(commandId);
            } catch (RuntimeException e) {
                log.warn("교통 대기 명령 재시도 실패: commandId={}", commandId, e);
            }
        }
    }
}
