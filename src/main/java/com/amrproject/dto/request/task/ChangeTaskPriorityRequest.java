package com.amrproject.dto.request.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 33단계: 대기(QUEUED) 작업의 우선순위 변경. 0(보통)~9(가장 급함). */
public record ChangeTaskPriorityRequest(
        @NotNull @Min(0) @Max(9) Integer priority
) {
}
