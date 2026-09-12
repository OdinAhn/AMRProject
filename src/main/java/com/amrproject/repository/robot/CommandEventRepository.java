package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.CommandEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandEventRepository extends JpaRepository<CommandEvent, Long> {
}
