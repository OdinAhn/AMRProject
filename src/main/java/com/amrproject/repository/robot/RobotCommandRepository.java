package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.RobotCommand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotCommandRepository extends JpaRepository<RobotCommand, Long> {
}
