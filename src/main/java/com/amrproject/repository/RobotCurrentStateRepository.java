package com.amrproject.repository;

import com.amrproject.domain.entity.RobotCurrentState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotCurrentStateRepository extends JpaRepository<RobotCurrentState, Long> {
}
