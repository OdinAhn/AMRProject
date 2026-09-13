package com.amrproject.domain.entity.alarm;

public enum AlarmType {
    ROBOT_COMM_LOST,
    LOCALIZATION_LOST,
    TASK_FAILED,
    LOAD_EXCEPTION,
    /** 27단계: 작업이 RECOVERY_REQUIRED로 전이됨(명령 실패·불명, 적재 중 취소, 작업 불가 보고) — recover 시 자동 해소 */
    TASK_RECOVERY_REQUIRED
}
