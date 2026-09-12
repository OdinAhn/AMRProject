package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.RobotCurrentState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotCurrentStateRepository extends JpaRepository<RobotCurrentState, Long> {
}
