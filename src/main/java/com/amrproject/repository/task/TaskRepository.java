package com.amrproject.repository.task;

import com.amrproject.domain.entity.task.Task;
import com.amrproject.domain.entity.task.TaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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




    /**
     * 31단계: 관리자 작업 목록(페이지). TaskResponse가 쓰는 연관(물류센터·작업장·배정·로봇)을 한 번에 가져와
     * 작업마다 추가 조회가 나가던 N+1을 없앤다. 필터 값이 null이면 그 조건은 무시한다.
     */
    @EntityGraph(attributePaths = {"site", "originStation", "destinationStation", "currentAssignment", "currentAssignment.robot"})
    @Query("select t from Task t where (:siteId is null or t.site.id = :siteId) and (:status is null or t.status = :status)")
    Page<Task> search(Long siteId, TaskStatus status, Pageable pageable);

    /** 31단계: 작업자 작업장 화면 — 출발/도착 어느 쪽이든 관련 있고 종료되지 않은 작업만 DB에서 거른다(예전엔 종료된 작업까지 전부 읽고 메모리에서 걸렀다). */
    @EntityGraph(attributePaths = {"site", "originStation", "destinationStation", "currentAssignment", "currentAssignment.robot"})
    @Query("""
            select t from Task t
            where (t.originStation.id = :stationId or t.destinationStation.id = :stationId)
              and t.status not in :excludedStatuses
            order by t.createdAt asc, t.id asc
            """)
    List<Task> findActiveByStation(Long stationId, Collection<TaskStatus> excludedStatuses);

    /**
     * 31단계: 로봇 배정 시 같은 작업을 동시에 배정하지 못하게 행을 잠근다(SELECT ... FOR UPDATE).
     * 잠금 조회는 트랜잭션 스냅샷이 아니라 최신 커밋 값을 읽는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findByIdForUpdate(Long id);
}
