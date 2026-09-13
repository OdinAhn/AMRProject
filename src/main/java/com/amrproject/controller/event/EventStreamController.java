package com.amrproject.controller.event;

import com.amrproject.config.RedisConfig;
import com.amrproject.domain.entity.event.OutboxEventStatus;
import com.amrproject.dto.response.event.EventEnvelope;
import com.amrproject.repository.event.OutboxEventRepository;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 15단계: 실시간 이벤트 SSE 스트림 (docs/API.md §5). Task/알람 이벤트를 아웃박스
 * 릴레이({@link com.amrproject.service.event.OutboxPublisher})가 발행하는 Redis 채널을 그대로 중계한다.
 * siteId/stationId 쿼리 필터는 이번 단계 범위 밖 — 전체 스트림을 그대로 전달한다.
 *
 * <p>27단계: Redis 구독을 백로그 조회보다 <b>먼저</b> 등록하고, 재생 순서·중복 제거는
 * {@link SseEventRelay}에 맡긴다 — 조회와 구독 사이에 발행된 이벤트가 유실되던 문제 수정.
 */
@RestController
public class EventStreamController {

    private static final Topic TOPIC = new ChannelTopic(RedisConfig.EVENT_CHANNEL);

    private final OutboxEventRepository outboxEventRepository;
    private final RedisMessageListenerContainer listenerContainer;
    private final JsonMapper jsonMapper;

    public EventStreamController(
            OutboxEventRepository outboxEventRepository,
            RedisMessageListenerContainer listenerContainer,
            JsonMapper jsonMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.listenerContainer = listenerContainer;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping(path = "/api/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId) {
        SseEmitter emitter = new SseEmitter(0L);
        SseEventRelay relay = new SseEventRelay(
                (eventId, json) -> emitter.send(SseEmitter.event().id(String.valueOf(eventId)).data(json, MediaType.APPLICATION_JSON)),
                lastEventId != null
        );

        MessageListener listener = (message, pattern) -> onMessage(emitter, relay, message);
        listenerContainer.addMessageListener(listener, TOPIC);

        Runnable unsubscribe = () -> listenerContainer.removeMessageListener(listener, TOPIC);
        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(unsubscribe);
        emitter.onError(ex -> unsubscribe.run());

        if (lastEventId != null) {
            List<SseEventRelay.Event> backlog = outboxEventRepository
                    .findByIdGreaterThanAndStatusOrderByIdAsc(lastEventId, OutboxEventStatus.PUBLISHED).stream()
                    .map(event -> new SseEventRelay.Event(event.getId(), jsonMapper.writeValueAsString(EventEnvelope.from(event))))
                    .toList();
            if (!relay.replay(backlog)) {
                unsubscribe.run();
                emitter.complete();
            }
        }

        return emitter;
    }

    /**
     * 19단계: 라이브 메시지에도 SSE id를 붙인다 — 안 붙이면 백로그 재생 이후 계속
     * 라이브로만 받은 클라이언트가 재접속할 때 Last-Event-ID가 오래된 값에 머문다.
     */
    private void onMessage(SseEmitter emitter, SseEventRelay relay, Message message) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        EventEnvelope envelope = jsonMapper.readValue(json, EventEnvelope.class);
        if (!relay.onLive(new SseEventRelay.Event(envelope.eventId(), json))) {
            emitter.complete();
        }
    }
}
