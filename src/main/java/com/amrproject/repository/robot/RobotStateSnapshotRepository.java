package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.RobotStateSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface RobotStateSnapshotRepository extends JpaRepository<RobotStateSnapshot, Long> {

    /** 31단계: 상태 이력은 최근 N건만 준다(예전엔 개수 제한 없이 전부). */
    List<RobotStateSnapshot> findByRobotIdOrderByReportedAtDesc(Long robotId, Pageable pageable);

    /** 31단계 보관 정책: 수신 시각이 cutoff보다 오래된 이력을 최대 limit건 지운다(긴 잠금을 피하려고 나눠 지운다). */
    @Modifying
    @Query(value = "delete from robot_state_snapshots where received_at < :cutoff limit :limit", nativeQuery = true)
    int deleteReceivedBefore(Instant cutoff, int limit);
}
