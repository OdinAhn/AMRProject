package com.amrproject.repository;

import com.amrproject.domain.entity.TaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {

    List<TaskEvent> findByTaskIdOrderByCreatedAtAsc(Long taskId);
}
