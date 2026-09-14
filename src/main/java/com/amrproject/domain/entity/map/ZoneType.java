package com.amrproject.domain.entity.map;

public enum ZoneType {
    MUTEX_ZONE,
    SAFETY_ZONE,
    /** 48단계: 할 일 없는 로봇이 쉬는 대기 구역(예: 선반이 모여 있는 곳 아래). 구역마다 고르게 채운다 */
    PARKING
}
