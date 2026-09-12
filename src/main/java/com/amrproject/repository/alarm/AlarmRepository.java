package com.amrproject.repository.alarm;

import com.amrproject.domain.entity.alarm.Alarm;
import com.amrproject.domain.entity.alarm.AlarmSourceType;
import com.amrproject.domain.entity.alarm.AlarmStatus;
import com.amrproject.domain.entity.alarm.AlarmType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlarmRepository extends JpaRepository<Alarm, Long> {

    List<Alarm> findAllByOrderByCreatedAtDesc();

    Optional<Alarm> findFirstByAlarmTypeAndSourceTypeAndSourceIdAndStatusIn(
            AlarmType alarmType, AlarmSourceType sourceType, Long sourceId, List<AlarmStatus> statuses
    );
}
