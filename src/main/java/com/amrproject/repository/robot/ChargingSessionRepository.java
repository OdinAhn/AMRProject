package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.ChargingSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargingSessionRepository extends JpaRepository<ChargingSession, Long> {
}
