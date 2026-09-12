package com.amrproject.repository;

import com.amrproject.domain.entity.Alarm;
import com.amrproject.domain.entity.AlarmSourceType;
import com.amrproject.domain.entity.AlarmStatus;
import com.amrproject.domain.entity.AlarmType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlarmRepository extends JpaRepository<Alarm, Long> {

    List<Alarm> findAllByOrderByCreatedAtDesc();

    Optional<Alarm> findFirstByAlarmTypeAndSourceTypeAndSourceIdAndStatusIn(
            AlarmType alarmType, AlarmSourceType sourceType, Long sourceId, List<AlarmStatus> statuses
    );
}
