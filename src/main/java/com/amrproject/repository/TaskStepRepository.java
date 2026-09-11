package com.amrproject.repository;

import com.amrproject.domain.entity.TaskStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskStepRepository extends JpaRepository<TaskStep, Long> {

    List<TaskStep> findByTaskIdOrderByStepNoAsc(Long taskId);

    Optional<TaskStep> findByTaskIdAndStepNo(Long taskId, Integer stepNo);
}
