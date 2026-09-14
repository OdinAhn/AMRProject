package com.amrproject.robot.mqtt;

import java.util.List;

/**
 * VDA 5050 "order" 메시지의 축소 구현 — 목적지 노드 하나짜리 주문만 표현한다.
 * 전체 스펙의 edges/orderUpdateId 재발행 등은 이번 범위에 포함하지 않는다 (docs/API.md §7 참고).
 *
 * <p>43단계: nodeId는 작업장에 연결된 활성 지도 노드의 코드다(연결이 없으면 작업장 코드). 충전 주문은 목적지 노드에
 * {@code startCharging} 동작을 싣는다.
 *
 * <p>46단계: 교통 관리가 경로를 예약했으면 출발 노드부터 목적지까지 <b>전체 경로</b>(nodes 0,2,4… + edges 1,3,…)를 보내 로봇이 예약한 길로만
 * 가게 한다({@code app.robot-gateway.mqtt.full-route}, 기본 true). 경로가 없거나 끄면 예전처럼 목적지 노드 하나다.
 */
public record Vda5050Order(
        long headerId,
        String timestamp,
        String version,
        String manufacturer,
        String serialNumber,
        String orderId,
        int orderUpdateId,
        List<Node> nodes,
        /** 46단계: 전체 경로 주문의 노드 사이 통로(노드가 하나면 비어 있음) */
        List<Edge> edges
) {
    public record Node(String nodeId, int sequenceId, boolean released, List<Action> actions) {

        /** 동작 없는 이동 노드(18단계 형식). */
        public Node(String nodeId, int sequenceId, boolean released) {
            this(nodeId, sequenceId, released, List.of());
        }
    }

    /** 46단계: 경로의 통로. sequenceId는 노드(짝수) 사이의 홀수 */
    public record Edge(String edgeId, int sequenceId, boolean released, String startNodeId, String endNodeId) {
    }

    /** blockingType: NONE, SOFT, HARD */
    public record Action(String actionType, String actionId, String blockingType) {
    }
}
