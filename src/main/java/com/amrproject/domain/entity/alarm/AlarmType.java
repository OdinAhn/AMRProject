package com.amrproject.domain.entity.alarm;

public enum AlarmType {
    ROBOT_COMM_LOST,
    LOCALIZATION_LOST,
    TASK_FAILED,
    LOAD_EXCEPTION,
    /** 27단계: 작업이 RECOVERY_REQUIRED로 전이됨(명령 실패·불명, 적재 중 취소, 작업 불가 보고) — recover 시 자동 해소 */
    TASK_RECOVERY_REQUIRED,
    /** 33단계: 로봇이 비상정지(EMERGENCY_STOP) 상태를 보고함 — 해제 보고 시 자동 해소 */
    ROBOT_EMERGENCY_STOP,
    /** 46단계: 로봇이 교통 대기(경로가 막힘)로 기준 시간 넘게 출발하지 못함 — 출발하거나 명령이 끝나면 자동 해소 */
    TRAFFIC_BLOCKED
}
