package com.amrproject.repository;

import com.amrproject.domain.entity.Robot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RobotRepository extends JpaRepository<Robot, Long> {

    List<Robot> findBySiteId(Long siteId);

    boolean existsByCode(String code);
}
