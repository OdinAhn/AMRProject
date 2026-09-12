package com.amrproject.repository;

import com.amrproject.domain.entity.Task;
import com.amrproject.domain.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByIdempotencyKey(String idempotencyKey);

    Optional<Task> findByExternalRef(String externalRef);

    List<Task> findBySiteIdAndStatusOrderByPriorityDescCreatedAtAsc(Long siteId, TaskStatus status);

    List<Task> findBySiteId(Long siteId);

    List<Task> findByStatus(TaskStatus status);

    List<Task> findBySiteIdAndStatus(Long siteId, TaskStatus status);

    List<Task> findByOriginStationIdOrDestinationStationId(Long originStationId, Long destinationStationId);
}
