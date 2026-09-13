package com.amrproject.service.robot;

import com.amrproject.domain.entity.event.OutboxEvent;
import com.amrproject.repository.event.OutboxEventRepository;
import org.springframework.stereotype.Component;

/**
 * 30단계: 로봇 상태가 바뀌었다는 실시간 이벤트({@code robot.state_changed}, aggregateType ROBOT)를 outbox에 남긴다.
 * 15단계에는 로봇 상태 보고가 고빈도라 스로틀링 설계 없이는 스트림에 넣지 않았다(API.md §5 열린 질문). 여기서는
 * 연결·동작·배터리·위치 같은 <b>상태 축이 실제로 바뀔 때만</b> 기록하고, 하트비트(수신 시각만 갱신)는 기록하지 않는다.
 * payload는 비워 둔다 — 화면은 이 이벤트를 받으면 필요한 조회(대시보드 개요 등)를 다시 한다.
 *
 * <p>호출자의 트랜잭션 안에서 저장되므로 상태 변경이 롤백되면 이벤트도 함께 사라진다.
 */
@Component
public class RobotStateEventRecorder {

    public static final String EVENT_TYPE = "robot.state_changed";

    private final OutboxEventRepository outboxEventRepository;

    public RobotStateEventRecorder(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    public void recordChanged(Long robotId) {
        outboxEventRepository.save(new OutboxEvent("ROBOT", robotId, EVENT_TYPE, null));
    }
}
