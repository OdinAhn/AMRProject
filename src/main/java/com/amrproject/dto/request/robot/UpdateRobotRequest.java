package com.amrproject.dto.request.robot;

public record UpdateRobotRequest(
        String name,
        String model,
        String manufacturer
) {
}
