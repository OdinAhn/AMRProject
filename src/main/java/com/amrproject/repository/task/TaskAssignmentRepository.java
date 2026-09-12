package com.amrproject.repository.task;

import com.amrproject.domain.entity.task.TaskAssignment;
import com.amrproject.domain.entity.task.TaskAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {

    Optional<TaskAssignment> findByRobotIdAndStatus(Long robotId, TaskAssignmentStatus status);
}
