package com.amrproject.controller.event;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 27단계: SSE 연결 하나에 대한 "백로그 재생 + 실시간 중계" 순서 제어.
 *
 * <p>예전 {@link EventStreamController}는 Last-Event-ID 백로그를 DB에서 조회·전송한 <b>뒤에</b>
 * Redis 구독을 등록해서, 그 사이에 발행된 이벤트는 백로그 조회에도 안 잡히고 구독에도 안
 * 잡혀 영영 유실됐다. 이제 구독을 먼저 등록하고, 백로그를 재생하는 동안 들어온 실시간
 * 이벤트는 버퍼에 쌓았다가 재생이 끝난 뒤 내보낸다. 백로그로 이미 보낸 이벤트 id는
 * 버퍼에서 건너뛴다(구독 등록과 백로그 조회 사이에 발행된 이벤트는 양쪽에 다 잡힐 수 있다).
 *
 * <p>중복 판정은 "마지막으로 보낸 id보다 작거나 같으면 건너뛰기"가 아니라 백로그로 보낸 id 집합으로
 * 한다 — Kafka 실패 재시도 등으로 Redis에 id 순서가 뒤바뀌어 도착할 수 있어, 크기 비교로 거르면
 * 늦게 도착한 정상 이벤트까지 버리게 된다.
 *
 * <p>실시간 이벤트는 Redis 리스너 컨테이너 스레드에서, 백로그 재생은 요청 스레드에서 호출되므로
 * 전송은 모두 이 객체의 모니터로 직렬화한다. 백로그 전송 중에는 락을 잡지 않아 리스너 스레드
 * (모든 SSE 연결이 공유)를 오래 막지 않는다 — 그동안 들어오는 실시간 이벤트는 버퍼에만 쌓인다.
 */
class SseEventRelay {

    interface Sender {
        void send(long eventId, String json) throws IOException;
    }

    record Event(long eventId, String json) {
    }

    private final Sender sender;
    private final Set<Long> replayedIds = new HashSet<>();
    private List<Event> liveBuffer;
    private boolean closed;

    /**
     * @param replaying 백로그를 재생할 예정이면 true — {@link #replay}가 끝날 때까지 실시간 이벤트를 버퍼링한다
     */
    SseEventRelay(Sender sender, boolean replaying) {
        this.sender = sender;
        this.liveBuffer = replaying ? new ArrayList<>() : null;
    }

    /** Redis 구독으로 받은 실시간 이벤트. 전송에 실패하면 false(연결 종료 필요). */
    synchronized boolean onLive(Event event) {
        if (closed) {
            return false;
        }
        if (liveBuffer != null) {
            liveBuffer.add(event);
            return true;
        }
        return sendOrClose(event);
    }

    /** 백로그(오름차순)를 보낸 뒤 버퍼에 쌓인 실시간 이벤트를 이어서 보낸다. 전송에 실패하면 false. */
    boolean replay(List<Event> backlog) {
        for (Event event : backlog) {
            synchronized (this) {
                if (!sendOrClose(event)) {
                    return false;
                }
                replayedIds.add(event.eventId());
            }
        }
        synchronized (this) {
            List<Event> buffered = liveBuffer;
            liveBuffer = null;
            for (Event event : buffered) {
                if (replayedIds.contains(event.eventId())) {
                    continue;
                }
                if (!sendOrClose(event)) {
                    return false;
                }
            }
            replayedIds.clear();
            return true;
        }
    }

    private boolean sendOrClose(Event event) {
        if (closed) {
            return false;
        }
        try {
            sender.send(event.eventId(), event.json());
            return true;
        } catch (IOException e) {
            closed = true;
            return false;
        }
    }
}
