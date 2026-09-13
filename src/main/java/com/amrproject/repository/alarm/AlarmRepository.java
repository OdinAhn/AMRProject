package com.amrproject.repository.alarm;

import com.amrproject.domain.entity.alarm.Alarm;
import com.amrproject.domain.entity.alarm.AlarmSeverity;
import com.amrproject.domain.entity.alarm.AlarmSourceType;
import com.amrproject.domain.entity.alarm.AlarmStatus;
import com.amrproject.domain.entity.alarm.AlarmType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AlarmRepository extends JpaRepository<Alarm, Long> {

    /** 31단계: 알람 목록(페이지). 예전엔 전체를 읽어 메모리에서 필터링했다. 필터 값이 null이면 그 조건은 무시한다. */
    @Query("select a from Alarm a where (:status is null or a.status = :status) and (:severity is null or a.severity = :severity) and (:sourceType is null or a.sourceType = :sourceType)")
    Page<Alarm> search(AlarmStatus status, AlarmSeverity severity, AlarmSourceType sourceType, Pageable pageable);

    /** 30단계 대시보드: 물류센터 소속 로봇·작업을 대상으로 한 열린 알람 수. alarms에는 site 컬럼이 없어 대상 테이블로 거슬러 올라간다. */
    @Query("""
            select count(a) from Alarm a
            where a.status in :statuses
              and ((a.sourceType = com.amrproject.domain.entity.alarm.AlarmSourceType.ROBOT
                    and a.sourceId in (select r.id from Robot r where r.site.id = :siteId))
                or (a.sourceType = com.amrproject.domain.entity.alarm.AlarmSourceType.TASK
                    and a.sourceId in (select t.id from Task t where t.site.id = :siteId)))
            """)
    long countBySiteAndStatusIn(Long siteId, List<AlarmStatus> statuses);

    Optional<Alarm> findFirstByAlarmTypeAndSourceTypeAndSourceIdAndStatusIn(
            AlarmType alarmType, AlarmSourceType sourceType, Long sourceId, List<AlarmStatus> statuses
    );
}
