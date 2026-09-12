package com.amrproject.robot.mqtt;

import java.util.List;

/**
 * VDA 5050 "order" 메시지의 축소 구현 — 목적지 station 하나짜리 단일 노드만 표현한다.
 * 전체 스펙의 edges/actions/orderUpdateId 재발행 등은 이번 범위에 포함하지 않는다
 * (docs/API.md §7 참고).
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
    public record Node(String nodeId, int sequenceId, boolean released) {
    }
}
