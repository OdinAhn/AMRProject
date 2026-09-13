package com.amrproject.dto.request.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param priority 33단계: 0(보통)~9(가장 급함). 생략하면 0. 자동 배정은 우선순위가 높은 작업부터, 같으면 먼저 들어온 작업부터 배정한다.
 */
public record CreateTaskRequest(
        @NotNull Long originStationId,
        @NotNull Long destinationStationId,
        @Min(0) @Max(9) Integer priority
) {

    /** 33단계 이전 요청(우선순위 없음)과의 호환용. */
    public CreateTaskRequest(Long originStationId, Long destinationStationId) {
        this(originStationId, destinationStationId, null);
    }
}
