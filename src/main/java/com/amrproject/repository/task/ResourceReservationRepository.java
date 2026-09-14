package com.amrproject.repository.task;

import com.amrproject.domain.entity.task.ResourceReservation;
import com.amrproject.domain.entity.task.ResourceReservationStatus;
import com.amrproject.domain.entity.task.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ResourceReservationRepository extends JpaRepository<ResourceReservation, Long> {

    int countByResourceTypeAndResourceIdAndStatus(ResourceType resourceType, Long resourceId, ResourceReservationStatus status);

    /**
     * 31단계: 수용량 검사용 HELD 예약 수를 잠금 조회(FOR SHARE)로 센다. 작업장 행을 잠근 뒤라도 일반 SELECT는 트랜잭션 스냅샷을 읽어
     * 그 사이 다른 트랜잭션이 커밋한 예약을 못 볼 수 있다(REPEATABLE READ) — 잠금 조회는 최신 커밋 값을 읽는다.
     */
    @Query(value = "select count(*) from resource_reservations where resource_type = :resourceType and resource_id = :resourceId and status = 'HELD' for share", nativeQuery = true)
    int countHeldForShare(String resourceType, Long resourceId);

    List<ResourceReservation> findByRobotIdAndStatus(Long robotId, ResourceReservationStatus status);

    /** 46단계: 교통 관리 — 이 로봇들의 HELD 경로 예약을 공유 잠금으로 읽는다(최신 커밋 값). */
    @Query(value = "select * from resource_reservations where resource_type = 'NODE' and status = 'HELD' and robot_id in (:robotIds) for share", nativeQuery = true)
    List<ResourceReservation> findHeldNodeReservationsOfRobotsForShare(Collection<Long> robotIds);

    List<ResourceReservation> findByRobotIdAndResourceTypeAndStatus(Long robotId, ResourceType resourceType, ResourceReservationStatus status);

    /** 46단계: 경로 예약 수(테스트·운영 확인용). */
    int countByRobotIdAndResourceTypeAndStatus(Long robotId, ResourceType resourceType, ResourceReservationStatus status);
}
