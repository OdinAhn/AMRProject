package com.amrproject.repository;

import com.amrproject.domain.entity.RobotStateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RobotStateSnapshotRepository extends JpaRepository<RobotStateSnapshot, Long> {

    List<RobotStateSnapshot> findByRobotIdOrderByReportedAtDesc(Long robotId);
}
