package com.amrproject.repository;

import com.amrproject.domain.entity.ChargingSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargingSessionRepository extends JpaRepository<ChargingSession, Long> {
}
