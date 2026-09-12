package com.amrproject.repository.event;

import com.amrproject.domain.entity.event.OutboxEvent;
import com.amrproject.domain.entity.event.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEventStatus status);

    List<OutboxEvent> findByIdGreaterThanAndStatusOrderByIdAsc(Long id, OutboxEventStatus status);
}
