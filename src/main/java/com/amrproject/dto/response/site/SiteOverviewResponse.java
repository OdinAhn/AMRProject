package com.amrproject.dto.response.site;

import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.MotionState;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import com.amrproject.domain.entity.robot.SafetyState;
import com.amrproject.domain.entity.task.TaskStatus;

import java.util.List;
import java.util.Map;

/**
 * 30단계: 관제 대시보드 한 화면에 필요한 물류센터 현황. 로봇 상태가 한 번도 보고되지 않은 로봇은
 * connectionState/motionState/batteryPct가 null이다.
 */
public record SiteOverviewResponse(
        Long siteId,
        /** 대시보드 지도에 쓸 활성 지도 버전(없으면 null) */
        Long activeMapVersionId,
        List<RobotSummary> robots,
        /** 종료되지 않은 상태별 작업 수 — 0건인 상태도 0으로 채운다 */
        Map<TaskStatus, Long> openTaskCounts,
        /** 이 물류센터의 로봇·작업을 대상으로 한 미확인(OPEN)·확인(ACKNOWLEDGED) 알람 수 */
        long openAlarmCount
) {

    public record RobotSummary(
            Long robotId,
            String code,
            String name,
            RobotLifecycleStatus lifecycleStatus,
            ConnectionState connectionState,
            MotionState motionState,
            /** 33단계: 비상·보호 정지 여부 */
            SafetyState safetyState,
            Integer batteryPct,
            Long currentNodeId,
            Long currentTaskId,
            String currentTaskNo
    ) {
    }
}
