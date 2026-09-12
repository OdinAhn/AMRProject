package com.amrproject.service;

import com.amrproject.config.KafkaConfig;
import com.amrproject.config.RedisConfig;
import com.amrproject.domain.entity.OutboxEvent;
import com.amrproject.domain.entity.OutboxEventStatus;
import com.amrproject.dto.response.EventEnvelope;
import com.amrproject.repository.OutboxEventRepository;
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

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByIdAsc(OutboxEventStatus.PENDING);
        for (OutboxEvent event : pending) {
            String json = jsonMapper.writeValueAsString(EventEnvelope.from(event));
            redisTemplate.convertAndSend(RedisConfig.EVENT_CHANNEL, json);
            kafkaTemplate.send(KafkaConfig.EVENT_TOPIC, String.valueOf(event.getId()), json);
            event.markPublished();
        }
    }
}
