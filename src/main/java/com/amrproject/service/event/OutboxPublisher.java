package com.amrproject.service.event;

import com.amrproject.config.KafkaConfig;
import com.amrproject.config.RedisConfig;
import com.amrproject.domain.entity.event.OutboxEvent;
import com.amrproject.dto.response.event.EventEnvelope;
import com.amrproject.repository.event.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 트랜잭셔널 아웃박스 릴레이 (docs/API.md §5). tasks/알람 상태 변경 시 같은
 * 트랜잭션 안에서 기록된 outbox_events(PENDING)를 주기적으로 읽어 두 곳에
 * 발행한다 — Redis Pub/Sub(15단계, 실시간 SSE 팬아웃)과 Kafka(16단계, 내구성
 * 있는 이벤트 로그) — 그리고 PUBLISHED로 마킹한다.
 *
 * <p>31단계: 서버를 여러 대 띄워도 같은 이벤트를 중복 발행하지 않도록 PENDING 행을 {@code FOR UPDATE SKIP LOCKED}로
 * 잠그며 가져온다(다른 인스턴스가 잡은 행은 건너뜀). 또 한 주기 전체를 하나의 트랜잭션으로 묶지 않고
 * {@link #BATCH_SIZE}건씩 별도 트랜잭션으로 처리한다 — Kafka 응답을 기다리는 동안 DB 커넥션·행 잠금을 쥐는 시간을
 * 배치 하나로 제한하기 위해서다.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5;
    static final int BATCH_SIZE = 100;
    /** 한 주기에 처리할 최대 배치 수 — 대기 이벤트가 계속 쌓여도 스케줄러 스레드를 무한히 붙잡지 않는다. */
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final OutboxEventRepository outboxEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            StringRedisTemplate redisTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 가득 찬 배치를 모두 성공적으로 발행했으면 다음 배치를 이어서 처리한다. 배치가 덜 찼거나(더 없음) 실패가 있었으면
     * 이번 주기를 끝낸다 — 실패한 이벤트는 PENDING으로 남아 다음 주기(1초 뒤)에 재시도되고, 같은 주기 안에서
     * 곧바로 다시 집어 들어 헛돌지 않는다.
     */
    @Scheduled(fixedDelay = 1000)
    public void publishPending() {
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            BatchResult result = transactionTemplate.execute(status -> publishBatch());
            if (result == null || result.processed() < BATCH_SIZE || result.failed() > 0) {
                return;
            }
        }
    }

    /**
     * 이벤트 하나씩 독립적으로 발행한다 — 한 이벤트의 발행이 실패해도(예: Kafka
     * 브로커 unreachable) 나머지 이벤트가 그 뒤에서 계속 막히지 않게 한다.
     * 실패한 이벤트는 PENDING으로 남아 다음 주기에 재시도된다(레디스/카프카 둘 다
     * 성공해야만 PUBLISHED로 마킹 — 최소 한 번 전달을 보장).
     *
     * <p>27단계: {@code KafkaTemplate.send()}는 비동기라 브로커가 거부·타임아웃해도 예외를
     * 던지지 않고 실패한 future만 돌려준다 — 예전엔 그 결과를 안 봐서 Kafka에 실제로 안 간
     * 이벤트도 PUBLISHED로 마킹됐다. 이제 결과를 {@link #KAFKA_SEND_TIMEOUT_SECONDS}까지
     * 기다려 확인한다. 또 Kafka를 Redis보다 먼저 보낸다 — Kafka 실패로 재시도될 때 SSE
     * 클라이언트가 같은 이벤트를 두 번 받지 않게 하기 위해서다.
     */
    private BatchResult publishBatch() {
        List<OutboxEvent> pending = outboxEventRepository.lockPendingBatch(BATCH_SIZE);
        int failed = 0;
        for (OutboxEvent event : pending) {
            try {
                String json = jsonMapper.writeValueAsString(EventEnvelope.from(event));
                kafkaTemplate.send(KafkaConfig.EVENT_TOPIC, String.valueOf(event.getId()), json)
                        .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                redisTemplate.convertAndSend(RedisConfig.EVENT_CHANNEL, json);
                event.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("outbox 발행이 중단됐습니다 — 남은 이벤트는 다음 주기에 재시도합니다.");
                return new BatchResult(pending.size(), failed + 1);
            } catch (Exception e) {
                failed++;
                log.warn("outbox_events(id={}) 발행 실패 — 다음 주기에 재시도합니다.", event.getId(), e);
            }
        }
        return new BatchResult(pending.size(), failed);
    }

    private record BatchResult(int processed, int failed) {
    }
}
