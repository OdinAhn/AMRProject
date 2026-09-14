package com.amrproject.repository.event;

import com.amrproject.domain.entity.event.OutboxEvent;
import com.amrproject.domain.entity.event.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 31단계: 발행 대기 이벤트를 잠그며 가져온다. {@code SKIP LOCKED}라 여러 서버 인스턴스가 동시에 릴레이를 돌려도
     * 다른 인스턴스가 잡고 있는 행은 건너뛰어, 같은 이벤트를 중복 발행하지 않는다(MySQL 8.0+).
     */
    @Query(value = "select * from outbox_events where status = 'PENDING' order by id limit :limit for update skip locked", nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(int limit);

    /** 31단계 보관 정책: 발행 완료(PUBLISHED)된 지 오래된 이벤트를 최대 limit건 지운다. PENDING은 지우지 않는다. */
    @Modifying
    @Query(value = "delete from outbox_events where status = 'PUBLISHED' and created_at < :cutoff limit :limit", nativeQuery = true)
    int deletePublishedBefore(Instant cutoff, int limit);

    List<OutboxEvent> findByIdGreaterThanAndStatusOrderByIdAsc(Long id, OutboxEventStatus status);

    /** 35단계 메트릭: 발행 대기 이벤트 수(릴레이가 밀리면 계속 늘어난다). */
    long countByStatus(OutboxEventStatus status);
}
