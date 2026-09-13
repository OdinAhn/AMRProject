package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.Robot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RobotRepository extends JpaRepository<Robot, Long> {

    List<Robot> findBySiteId(Long siteId);

    boolean existsByCode(String code);

    Optional<Robot> findByCode(String code);
}
