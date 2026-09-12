package com.amrproject.service;

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

/**
 * 트랜잭셔널 아웃박스 릴레이 (docs/API.md §5). tasks/알람 상태 변경 시 같은
 * 트랜잭션 안에서 기록된 outbox_events(PENDING)를 주기적으로 읽어 두 곳에
 * 발행한다 — Redis Pub/Sub(15단계, 실시간 SSE 팬아웃)과 Kafka(16단계, 내구성
 * 있는 이벤트 로그) — 그리고 PUBLISHED로 마킹한다.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

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
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByIdAsc(OutboxEventStatus.PENDING);
        for (OutboxEvent event : pending) {
            try {
                String json = jsonMapper.writeValueAsString(EventEnvelope.from(event));
                redisTemplate.convertAndSend(RedisConfig.EVENT_CHANNEL, json);
                kafkaTemplate.send(KafkaConfig.EVENT_TOPIC, String.valueOf(event.getId()), json);
                event.markPublished();
            } catch (Exception e) {
                log.warn("outbox_events(id={}) 발행 실패 — 다음 주기에 재시도합니다.", event.getId(), e);
            }
        }
    }
}
