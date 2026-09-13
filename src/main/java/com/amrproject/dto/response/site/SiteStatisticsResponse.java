package com.amrproject.dto.response.site;

import com.amrproject.domain.entity.alarm.AlarmSeverity;
import com.amrproject.domain.entity.alarm.AlarmType;

import java.time.LocalDate;
import java.util.List;

/**
 * 34단계: 물류센터 운영 통계. 기간은 zone 기준 날짜 [from, to] (양 끝 포함)이다.
 * 비율·평균은 계산할 대상이 없으면 null이다(0과 구분).
 */
public record SiteStatisticsResponse(
        Long siteId,
        LocalDate from,
        LocalDate to,
        String zone,
        TaskSummary tasks,
        /** from~to 모든 날짜를 빠짐없이 채운다 */
        List<DailyCount> daily,
        /** 물류센터 소속 로봇 전체(배정이 없던 로봇도 0으로) */
        List<RobotUtilization> robots,
        List<AlarmCount> alarms
) {

    public record TaskSummary(
            /** 기간 안에 생성된 작업 수 */
            long created,
            /** 아래 세 값은 기간 안에 종료(completed_at)된 작업 수 */
            long succeeded,
            long failed,
            long canceled,
            /** 성공 / (성공 + 실패) × 100. 취소는 운영 판단이라 제외한다 */
            Double successRatePct,
            /** 성공 작업의 생성→완료 평균(초) */
            Long avgLeadTimeSeconds,
            /** 종료된 작업 중 로봇이 배정된 작업의 생성→첫 배정 평균(초) */
            Long avgWaitSeconds
    ) {
    }

    public record DailyCount(LocalDate date, long created, long succeeded, long failed, long canceled) {
    }

    public record RobotUtilization(
            Long robotId,
            String code,
            String name,
            /** 기간 안에 완료한 작업 수 */
            long completedTasks,
            /** 기간 안에서 작업이 배정돼 있던 시간(초) */
            long busySeconds,
            /** busySeconds / 기간 중 이미 지난 시간 × 100. 기간이 아직 시작되지 않았으면 null */
            Double utilizationPct
    ) {
    }

    public record AlarmCount(AlarmType alarmType, AlarmSeverity severity, long count) {
    }
}
