package com.amrproject.controller.event;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 27단계: SSE 백로그 재생과 실시간 이벤트의 순서·중복 제거 규칙을 네트워크 없이(Docker 불필요) 검증한다.
 */
class SseEventRelayTest {

    private final List<Long> sent = new ArrayList<>();

    @Test
    void 백로그_재생_중에_들어온_실시간_이벤트는_재생이_끝난_뒤_보낸다() {
        SseEventRelay relay = new SseEventRelay(this::record, true);

        relay.onLive(event(12));
        assertThat(sent).isEmpty();

        relay.replay(List.of(event(10), event(11)));

        assertThat(sent).containsExactly(10L, 11L, 12L);
    }

    @Test
    void 구독_등록과_백로그_조회_사이에_발행돼_양쪽에_잡힌_이벤트는_한_번만_보낸다() {
        SseEventRelay relay = new SseEventRelay(this::record, true);

        relay.onLive(event(11));
        relay.onLive(event(12));
        relay.replay(List.of(event(10), event(11)));

        assertThat(sent).containsExactly(10L, 11L, 12L);
    }

    @Test
    void 재생이_끝난_뒤에는_실시간_이벤트를_바로_보내고_id_순서가_뒤바뀌어도_버리지_않는다() {
        SseEventRelay relay = new SseEventRelay(this::record, true);
        relay.replay(List.of(event(10)));

        relay.onLive(event(13));
        relay.onLive(event(12));

        assertThat(sent).containsExactly(10L, 13L, 12L);
    }

    @Test
    void Last_Event_ID가_없으면_버퍼링하지_않고_바로_보낸다() {
        SseEventRelay relay = new SseEventRelay(this::record, false);

        relay.onLive(event(5));

        assertThat(sent).containsExactly(5L);
    }

    @Test
    void 전송이_실패하면_이후_이벤트는_보내지_않고_false를_반환한다() {
        SseEventRelay relay = new SseEventRelay((eventId, json) -> {
            throw new IOException("client disconnected");
        }, false);

        assertThat(relay.onLive(event(1))).isFalse();
        assertThat(relay.onLive(event(2))).isFalse();
    }

    private void record(Long eventId, String json) {
        sent.add(eventId);
    }

    @Test
    void id가_없는_표시전용_이벤트는_백로그와_무관하게_그대로_보낸다() {
        SseEventRelay relay = new SseEventRelay(this::record, true);

        relay.onLive(new SseEventRelay.Event(null, "{}"));
        relay.replay(List.of(event(10)));

        assertThat(sent).containsExactly(10L, null);
    }

    private static SseEventRelay.Event event(long id) {
        return new SseEventRelay.Event(id, "{\"eventId\":" + id + "}");
    }
}
