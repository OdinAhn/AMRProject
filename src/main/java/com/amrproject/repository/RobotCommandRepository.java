package com.amrproject.repository;

import com.amrproject.domain.entity.RobotCommand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotCommandRepository extends JpaRepository<RobotCommand, Long> {
}
