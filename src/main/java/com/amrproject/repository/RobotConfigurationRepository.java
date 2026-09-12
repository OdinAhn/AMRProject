package com.amrproject.repository;

import com.amrproject.domain.entity.RobotConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RobotConfigurationRepository extends JpaRepository<RobotConfiguration, Long> {

    List<RobotConfiguration> findByRobotIdOrderByVersionNoAsc(Long robotId);

    int countByRobotId(Long robotId);
}
