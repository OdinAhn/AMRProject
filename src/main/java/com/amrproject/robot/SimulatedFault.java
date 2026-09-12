package com.amrproject.robot;

/**
 * 관리자가 다음 명령에 주입할 수 있는 장애 유형 (docs/DESIGN.md §4 "시뮬레이터: ...장애...재현").
 */
public enum SimulatedFault {
    /** 명령이 로봇에 도달했지만 실행이 실패한 경우. */
    COMMAND_FAILURE,
    /** 응답이 없어 실행 여부를 알 수 없는 경우 (통신 단절/타임아웃). */
    COMMUNICATION_LOSS,
    /** 위치 추정 실패 (14단계: ROBOT_COMM_LOST 알람과 별개로 LOCALIZATION_LOST 알람을 검증하기 위한 도구). */
    LOCALIZATION_LOSS,
    /** 적재 센서 응답 불가 (14단계: LOAD_EXCEPTION 알람을 검증하기 위한 도구). */
    LOAD_SENSOR_MISMATCH
}
