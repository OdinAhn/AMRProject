package com.amrproject.controller;

import com.amrproject.domain.entity.event.OutboxEvent;
import com.amrproject.domain.entity.event.OutboxEventStatus;
import com.amrproject.repository.event.OutboxEventRepository;
import com.amrproject.service.OutboxPublisher;
import com.amrproject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 15단계: SSE 스트림 연결과 Last-Event-ID 백로그 재생 대상 조회를 검증한다.
 * 실시간 Redis 구독→푸시가 실제로 도착하는지는 자동화하지 않고 수동(curl)으로 검증한다
 * (docs 계획 참고 — MockMvc로 SSE 라이브 전달을 안정적으로 검증하기 어렵다).
 */
@AutoConfigureMockMvc
class EventStreamTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Test
    void SSE_엔드포인트는_text_event_stream으로_연결된다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/events/stream"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void Last_Event_ID_이후에_발행된_이벤트만_백로그_조회_대상이다() {
        OutboxEvent before = outboxEventRepository.save(new OutboxEvent("TASK", 1L, "task.created", null));
        outboxPublisher.publishPending();

        OutboxEvent after = outboxEventRepository.save(new OutboxEvent("TASK", 2L, "task.succeeded", null));
        outboxPublisher.publishPending();

        List<OutboxEvent> backlog = outboxEventRepository.findByIdGreaterThanAndStatusOrderByIdAsc(before.getId(), OutboxEventStatus.PUBLISHED);

        assertThat(backlog).extracting(OutboxEvent::getId).containsExactly(after.getId());
        assertThat(outboxEventRepository.findById(before.getId()).orElseThrow().getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEventRepository.findById(after.getId()).orElseThrow().getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }
}
