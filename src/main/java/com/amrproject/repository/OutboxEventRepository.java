package com.amrproject.repository;

import com.amrproject.domain.entity.OutboxEvent;
import com.amrproject.domain.entity.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEventStatus status);

    List<OutboxEvent> findByIdGreaterThanAndStatusOrderByIdAsc(Long id, OutboxEventStatus status);
}
