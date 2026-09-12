package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.RobotStateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RobotStateSnapshotRepository extends JpaRepository<RobotStateSnapshot, Long> {

    List<RobotStateSnapshot> findByRobotIdOrderByReportedAtDesc(Long robotId);
}
