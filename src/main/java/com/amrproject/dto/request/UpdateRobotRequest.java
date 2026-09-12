package com.amrproject.dto.request;

public record UpdateRobotRequest(
        String name,
        String model,
        String manufacturer
) {
}
