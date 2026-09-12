package com.amrproject.dto.request.robot;

import com.amrproject.robot.SimulatedFault;
import jakarta.validation.constraints.NotNull;

public record InjectFaultRequest(
        @NotNull SimulatedFault type
) {
}
