package com.amrproject.service;

import com.amrproject.config.RedisConfig;
import com.amrproject.domain.entity.OutboxEvent;
import com.amrproject.domain.entity.OutboxEventStatus;
import com.amrproject.dto.response.EventEnvelope;
import com.amrproject.repository.OutboxEventRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 15단계: 트랜잭셔널 아웃박스 릴레이 (docs/API.md §5). tasks/알람 상태 변경 시
 * 같은 트랜잭션 안에서 기록된 outbox_events(PENDING)를 주기적으로 읽어 Redis
 * Pub/Sub로 발행하고 PUBLISHED로 마킹한다.
 */
@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, StringRedisTemplate redisTemplate, JsonMapper jsonMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByIdAsc(OutboxEventStatus.PENDING);
        for (OutboxEvent event : pending) {
            String json = jsonMapper.writeValueAsString(EventEnvelope.from(event));
            redisTemplate.convertAndSend(RedisConfig.EVENT_CHANNEL, json);
            event.markPublished();
        }
    }
}
