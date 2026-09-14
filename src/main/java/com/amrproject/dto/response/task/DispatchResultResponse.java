package com.amrproject.dto.response.task;

import java.util.List;

public record DispatchResultResponse(
        List<Long> assignedTaskIds,
        List<Long> chargeTaskIds,
        /** 38단계: 배정마다 고른 로봇과 출발 작업장까지 통로 거리(지도 좌표 단위, 모르면 null) */
        List<Assignment> assignments,
        /** 48단계: 대기 구역으로 보낸 로봇 */
        List<Long> parkedRobotIds
) {

    public record Assignment(Long taskId, Long robotId, String robotCode, Double distanceToOrigin) {
    }
}
