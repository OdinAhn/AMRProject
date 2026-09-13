package com.amrproject.dto.request.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param priority 33단계: 0(보통)~9(가장 급함). 생략하면 0.
 */
public record CreateExternalTaskRequest(
        @NotNull Long originStationId,
        @NotNull Long destinationStationId,
        String externalRef,
        @Min(0) @Max(9) Integer priority
) {

    /** 33단계 이전 요청(우선순위 없음)과의 호환용. */
    public CreateExternalTaskRequest(Long originStationId, Long destinationStationId, String externalRef) {
        this(originStationId, destinationStationId, externalRef, null);
    }
}
