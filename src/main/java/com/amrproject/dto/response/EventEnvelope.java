package com.amrproject.dto.response;

import com.amrproject.domain.entity.OutboxEvent;

import java.time.Instant;

/**
 * 15단계: 실시간 이벤트 봉투 (docs/API.md §5). eventId는 ULID 대신 outbox_events.id를
 * 그대로 쓴다 — 단일 DB 시스템이라 정렬 가능한 정수 ID로 충분하다. stateVersion은
 * Task/Alarm에는 별도 버전 카운터가 없어 항상 null이다.
 */
public record EventEnvelope(
        Long eventId,
        String eventType,
        String aggregateType,
        Long aggregateId,
        Integer stateVersion,
        String payload,
        Instant occurredAt
) {

    public static EventEnvelope from(OutboxEvent event) {
        return new EventEnvelope(
                event.getId(),
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId(),
                null,
                event.getPayloadJson(),
                event.getCreatedAt()
        );
    }
}
