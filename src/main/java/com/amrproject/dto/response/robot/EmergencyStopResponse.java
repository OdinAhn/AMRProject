package com.amrproject.dto.response.robot;

import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotCommandStatus;

/**
 * 33단계: 비상정지 요청 결과. {@code commandStatus}는 정지 명령이 로봇에 전달·수락됐는지일 뿐, 로봇이 실제로 멈췄는지는
 * 로봇이 보고하는 {@code safetyState}로 확인한다(DESIGN.md §3 — 서버의 정지 요청 성공을 실제 정지 완료로 표시하지 않는다).
 */
public record EmergencyStopResponse(
        Long robotId,
        String robotCode,
        String commandNo,
        RobotCommandStatus commandStatus
) {

    public static EmergencyStopResponse from(RobotCommand command) {
        return new EmergencyStopResponse(
                command.getRobot().getId(),
                command.getRobot().getCode(),
                command.getCommandNo(),
                command.getStatus()
        );
    }
}
