package com.amrproject.repository;

import com.amrproject.domain.entity.AlarmEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmEventRepository extends JpaRepository<AlarmEvent, Long> {
}
