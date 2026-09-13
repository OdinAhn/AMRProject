package com.amrproject.service.event;

import com.amrproject.config.KafkaConfig;
import com.amrproject.config.RedisConfig;
import com.amrproject.domain.entity.event.OutboxEvent;
import com.amrproject.domain.entity.event.OutboxEventStatus;
import com.amrproject.dto.response.event.EventEnvelope;
import com.amrproject.repository.event.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 트랜잭셔널 아웃박스 릴레이 (docs/API.md §5). tasks/알람 상태 변경 시 같은
 * 트랜잭션 안에서 기록된 outbox_events(PENDING)를 주기적으로 읽어 두 곳에
 * 발행한다 — Redis Pub/Sub(15단계, 실시간 SSE 팬아웃)과 Kafka(16단계, 내구성
 * 있는 이벤트 로그) — 그리고 PUBLISHED로 마킹한다.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            StringRedisTemplate redisTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
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
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByIdAsc(OutboxEventStatus.PENDING);
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
                return;
            } catch (Exception e) {
                log.warn("outbox_events(id={}) 발행 실패 — 다음 주기에 재시도합니다.", event.getId(), e);
            }
        }
    }
}
