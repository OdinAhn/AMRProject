package com.amrproject.domain.entity.task;

public enum ResourceType {
    EDGE,
    ZONE,
    STATION,
    /** 46단계: 교통 관리 — 로봇이 지나갈 지도 노드(resource_id = nodes.id) */
    NODE
}
