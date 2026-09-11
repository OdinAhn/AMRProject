package com.amrproject.repository;

import com.amrproject.domain.entity.TaskAssignment;
import com.amrproject.domain.entity.TaskAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {

    Optional<TaskAssignment> findByRobotIdAndStatus(Long robotId, TaskAssignmentStatus status);
}
