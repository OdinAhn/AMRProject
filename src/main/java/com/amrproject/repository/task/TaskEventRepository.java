package com.amrproject.repository.task;

import com.amrproject.domain.entity.task.TaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {

    List<TaskEvent> findByTaskIdOrderByCreatedAtAsc(Long taskId);
}
