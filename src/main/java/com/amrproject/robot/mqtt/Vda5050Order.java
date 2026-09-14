package com.amrproject.robot.mqtt;

import java.util.List;

/**
 * VDA 5050 "order" 메시지의 축소 구현 — 목적지 노드 하나짜리 주문만 표현한다.
 * 전체 스펙의 edges/orderUpdateId 재발행 등은 이번 범위에 포함하지 않는다 (docs/API.md §7 참고).
 *
 * <p>43단계: nodeId는 작업장에 연결된 활성 지도 노드의 코드다(연결이 없으면 작업장 코드). 충전 주문은 목적지 노드에
 * {@code startCharging} 동작을 싣는다.
 */
public record Vda5050Order(
        long headerId,
        String timestamp,
        String version,
        String manufacturer,
        String serialNumber,
        String orderId,
        int orderUpdateId,
        List<Node> nodes
) {
    public record Node(String nodeId, int sequenceId, boolean released, List<Action> actions) {

        /** 동작 없는 이동 노드(18단계 형식). */
        public Node(String nodeId, int sequenceId, boolean released) {
            this(nodeId, sequenceId, released, List.of());
        }
    }

    /** blockingType: NONE, SOFT, HARD */
    public record Action(String actionType, String actionId, String blockingType) {
    }
}
