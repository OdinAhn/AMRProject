package com.amrproject.repository.event;

import com.amrproject.domain.entity.event.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** 37단계: 감사 로그 목록. 필터 값이 null이면 그 조건은 무시한다. */
    @Query("""
            select a from AuditLog a
            where (:actorUsername is null or a.actorUsername = :actorUsername)
              and (:targetType is null or a.targetType = :targetType)
              and (:targetId is null or a.targetId = :targetId)
            """)
    Page<AuditLog> search(String actorUsername, String targetType, Long targetId, Pageable pageable);

    /** 42단계 보관 정책: 기록 시각이 cutoff보다 오래된 감사 로그를 최대 limit건 지운다. */
    @Modifying
    @Query(value = "delete from audit_logs where created_at < :cutoff limit :limit", nativeQuery = true)
    int deleteCreatedBefore(Instant cutoff, int limit);
}
