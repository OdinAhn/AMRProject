package com.amrproject.repository.task;

import com.amrproject.domain.entity.task.TaskAssignment;
import com.amrproject.domain.entity.task.TaskAssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {

    /** 34단계 통계: 물류센터 로봇의 배정 중 [from, to)와 겹치는 것(아직 진행 중인 배정 포함). 로봇 가동률 계산용. */
    @Query("""
            select a from TaskAssignment a join fetch a.robot r
            where r.site.id = :siteId and a.assignedAt < :to and (a.releasedAt is null or a.releasedAt > :from)
            """)
    List<TaskAssignment> findOverlapping(Long siteId, Instant from, Instant to);

    Optional<TaskAssignment> findByRobotIdAndStatus(Long robotId, TaskAssignmentStatus status);

    /**
     * 31단계: 배정 직전 "이 로봇에 이미 진행 중인 배정이 있는가"를 잠금 조회로 확인한다. MySQL 기본 격리수준(REPEATABLE READ)에서
     * 일반 SELECT는 트랜잭션의 첫 조회 시점 스냅샷을 읽어, 로봇 행 잠금을 기다리는 동안 다른 트랜잭션이 커밋한 배정을 못 본다 —
     * 잠금 조회는 최신 커밋 값을 읽는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from TaskAssignment a where a.robot.id = :robotId and a.status = :status")
    Optional<TaskAssignment> findByRobotIdAndStatusForUpdate(Long robotId, TaskAssignmentStatus status);
}
