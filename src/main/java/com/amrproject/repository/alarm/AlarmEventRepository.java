package com.amrproject.repository.alarm;

import com.amrproject.domain.entity.alarm.AlarmEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmEventRepository extends JpaRepository<AlarmEvent, Long> {
}
