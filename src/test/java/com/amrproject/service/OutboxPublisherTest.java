package com.amrproject.service;

import com.amrproject.config.KafkaConfig;
import com.amrproject.domain.entity.event.OutboxEvent;
import com.amrproject.domain.entity.event.OutboxEventStatus;
import com.amrproject.repository.event.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 Redis/Kafka/DB 없이(Docker 불필요) 아웃박스 릴레이 로직만 검증한다.
 */
class OutboxPublisherTest {

    private final OutboxEventRepository outboxEventRepository = Mockito.mock(OutboxEventRepository.class);
    private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final KafkaTemplate<String, String> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxEventRepository, redisTemplate, kafkaTemplate, jsonMapper);
    }

    @Test
    void PENDING_이벤트를_Redis와_Kafka에_발행하고_PUBLISHED로_마킹한다() {
        OutboxEvent event = new OutboxEvent("TASK", 42L, "task.succeeded", null);
        when(outboxEventRepository.findByStatusOrderByIdAsc(OutboxEventStatus.PENDING)).thenReturn(List.of(event));

        publisher.publishPending();

        verify(redisTemplate).convertAndSend(eq("amr.events"), contains("\"aggregateType\":\"TASK\""));
        verify(redisTemplate).convertAndSend(eq("amr.events"), contains("\"aggregateId\":42"));
        verify(redisTemplate).convertAndSend(eq("amr.events"), contains("\"eventType\":\"task.succeeded\""));
        verify(kafkaTemplate).send(eq(KafkaConfig.EVENT_TOPIC), eq("null"), contains("\"aggregateId\":42"));
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void PENDING_이벤트가_없으면_발행하지_않는다() {
        when(outboxEventRepository.findByStatusOrderByIdAsc(OutboxEventStatus.PENDING)).thenReturn(List.of());

        publisher.publishPending();

        verify(redisTemplate, Mockito.never()).convertAndSend(any(), any());
        verify(kafkaTemplate, Mockito.never()).send(any(), any(), any());
    }

    /**
     * 회귀 테스트: Kafka 브로커가 unreachable할 때 첫 이벤트 발행이 예외를 던져도
     * 그 뒤 이벤트가 계속 처리돼야 한다 — 예전엔 예외가 루프를 통째로 중단시켜
     * 뒤에 있는 모든 PENDING 이벤트가 영원히 막히는 head-of-line 블로킹이 있었다.
     */
    @Test
    void 한_이벤트_발행이_실패해도_나머지_이벤트는_계속_처리한다() {
        OutboxEvent failing = new OutboxEvent("TASK", 1L, "task.a", null);
        OutboxEvent succeeding = new OutboxEvent("TASK", 2L, "task.b", null);
        when(outboxEventRepository.findByStatusOrderByIdAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(failing, succeeding));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenThrow(new RuntimeException("kafka unreachable"))
                .thenReturn(null);

        publisher.publishPending();

        assertThat(failing.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(succeeding.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        verify(redisTemplate, Mockito.times(2)).convertAndSend(any(), any());
    }
}
