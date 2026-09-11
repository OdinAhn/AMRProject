package com.amrproject.repository;

import com.amrproject.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
