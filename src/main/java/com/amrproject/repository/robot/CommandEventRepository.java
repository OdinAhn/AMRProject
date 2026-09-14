package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.CommandEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommandEventRepository extends JpaRepository<CommandEvent, Long> {

    /** 46단계: 교통 대기가 시작된 시각(첫 WAITING_FOR_TRAFFIC 이벤트). */
    Optional<CommandEvent> findFirstByCommandIdAndEventTypeOrderByIdAsc(Long commandId, String eventType);
}
