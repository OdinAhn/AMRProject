package com.amrproject.service;

import com.amrproject.config.KafkaConfig;
import com.amrproject.domain.entity.event.OutboxEvent;
import com.amrproject.domain.entity.event.OutboxEventStatus;
import com.amrproject.repository.event.OutboxEventRepository;
import com.amrproject.service.event.OutboxPublisher;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

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
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

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
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        assertThat(failing.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(succeeding.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        // 27단계: Kafka가 먼저라, Kafka에 실패한 이벤트는 Redis(SSE)로도 나가지 않는다 — 재시도 때 중복 전달 방지.
        verify(redisTemplate, Mockito.times(1)).convertAndSend(any(), any());
    }

    /**
     * 27단계 회귀 테스트: KafkaTemplate.send()는 비동기라 브로커 실패가 예외가 아니라 실패한
     * future로 온다 — 예전엔 결과를 확인하지 않아 이런 이벤트도 PUBLISHED로 마킹됐다.
     */
    @Test
    void Kafka_전송_future가_실패하면_PENDING으로_남기고_Redis로도_보내지_않는다() {
        OutboxEvent event = new OutboxEvent("TASK", 3L, "task.c", null);
        when(outboxEventRepository.findByStatusOrderByIdAsc(OutboxEventStatus.PENDING)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker rejected")));

        publisher.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        verify(redisTemplate, Mockito.never()).convertAndSend(any(), any());
    }
}
