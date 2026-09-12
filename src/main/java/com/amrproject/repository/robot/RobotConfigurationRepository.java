package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.RobotConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RobotConfigurationRepository extends JpaRepository<RobotConfiguration, Long> {

    List<RobotConfiguration> findByRobotIdOrderByVersionNoAsc(Long robotId);

    int countByRobotId(Long robotId);
}
