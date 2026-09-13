package com.amrproject.repository.task;

import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.task.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /** 30단계 대시보드: 물류센터의 상태별 작업 수. */
    interface StatusCount {
        TaskStatus getStatus();

        long getCount();
    }

    @Query("select t.status as status, count(t) as count from Task t where t.site.id = :siteId and t.status in :statuses group by t.status")
    List<StatusCount> countBySiteAndStatusIn(Long siteId, Collection<TaskStatus> statuses);

    Optional<Task> findByIdempotencyKey(String idempotencyKey);

    Optional<Task> findByExternalRef(String externalRef);

    List<Task> findBySiteIdAndStatusOrderByPriorityDescCreatedAtAsc(Long siteId, TaskStatus status);

    List<Task> findBySiteId(Long siteId);

    List<Task> findByStatus(TaskStatus status);

    List<Task> findBySiteIdAndStatus(Long siteId, TaskStatus status);

    List<Task> findByOriginStationIdOrDestinationStationId(Long originStationId, Long destinationStationId);
}
