package com.amrproject.domain.entity.robot;

public enum RobotCommandType {
    MOVE_TO,
    DOCK,
    UNDOCK,
    START_CHARGE,
    STOP_CHARGE,
    CANCEL,
    EMERGENCY_STOP,
    /** 47단계: 작업과 무관하게 다른 로봇의 길을 비키는 이동(경로는 payload_json의 route, 목적지는 마지막 노드) */
    REPOSITION
}
