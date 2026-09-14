package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.ConnectionState;
import com.amrproject.domain.entity.robot.RobotCurrentState;
import com.amrproject.domain.entity.robot.RobotLifecycleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 29단계의 연결 감시 쿼리는 엔티티를 읽어 고친 뒤 저장하는 대신 조건부 bulk update를 쓴다 — 같은 행을
 * 명령 처리(게이트웨이 report)가 동시에 갱신 중일 때, 먼저 읽은 오래된 행 전체를 덮어써
 * 명령 결과(motion/battery/version)를 잃는 lost update를 막기 위해서다. 조건을 UPDATE 시점에 다시
 * 평가하므로, 그 사이 명령이 커밋해 수신 시각이 새로워졌으면 0행이 갱신된다.
 */
public interface RobotCurrentStateRepository extends JpaRepository<RobotCurrentState, Long> {

    /** 41단계: 실제 로봇의 모드·안전·위치 보고를 반영할 때 행을 잠그고 최신 값을 읽는다(동시에 도는 명령 처리의 갱신을 덮어쓰지 않도록). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RobotCurrentState s where s.robotId = :robotId")
    Optional<RobotCurrentState> findByRobotIdForUpdate(Long robotId);

    /** 29단계: 운영 중이고 지정한 연결 상태인데 마지막 수신이 threshold보다 오래된 로봇 id. */
    @Query("""
            select s.robotId from RobotCurrentState s, Robot r
            where r.id = s.robotId
              and r.lifecycleStatus = :lifecycleStatus
              and s.connectionState = :connectionState
              and s.receivedAt < :threshold
            """)
    List<Long> findRobotIdsReceivedBefore(RobotLifecycleStatus lifecycleStatus, ConnectionState connectionState, Instant threshold);

    @Query("""
            select s.robotId from RobotCurrentState s, Robot r
            where r.id = s.robotId
              and r.lifecycleStatus = :lifecycleStatus
              and s.connectionState = :connectionState
            """)
    List<Long> findRobotIds(RobotLifecycleStatus lifecycleStatus, ConnectionState connectionState);

    /** 29단계 시뮬레이터 하트비트: 여전히 OFFLINE일 때만 ONLINE으로 복구한다. 갱신된 행 수(0 또는 1)를 반환한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RobotCurrentState s
               set s.stateVersion = s.stateVersion + 1,
                   s.connectionState = com.amrproject.domain.entity.robot.ConnectionState.ONLINE,
                   s.receivedAt = :now
             where s.robotId = :robotId
               and s.connectionState = com.amrproject.domain.entity.robot.ConnectionState.OFFLINE
            """)
    int reviveIfOffline(Long robotId, Instant now);

    /** 29단계: 여전히 ONLINE이고 수신이 오래됐을 때만 OFFLINE으로 바꾼다. 갱신된 행 수(0 또는 1)를 반환한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RobotCurrentState s
               set s.stateVersion = s.stateVersion + 1,
                   s.connectionState = com.amrproject.domain.entity.robot.ConnectionState.OFFLINE
             where s.robotId = :robotId
               and s.connectionState = com.amrproject.domain.entity.robot.ConnectionState.ONLINE
               and s.receivedAt < :threshold
            """)
    int markOfflineIfStale(Long robotId, Instant threshold);

    /** 29단계 시뮬레이터 하트비트: 운영 중이고 ONLINE인 로봇의 수신 시각만 갱신한다(상태 변화가 아니라 버전은 그대로). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RobotCurrentState s
               set s.receivedAt = :now
             where s.connectionState = com.amrproject.domain.entity.robot.ConnectionState.ONLINE
               and s.robotId in (select r.id from Robot r where r.lifecycleStatus = :lifecycleStatus)
            """)
    int touchOnline(RobotLifecycleStatus lifecycleStatus, Instant now);

    /**
     * 29단계 MQTT 텔레메트리: 수신 시각을 갱신하고 ONLINE으로 표시, 배터리 값이 있으면 반영한다.
     * 연결이나 배터리가 실제로 바뀔 때만 버전을 올린다 — MySQL은 SET 절을 왼쪽부터 평가하므로
     * stateVersion을 맨 앞에 둬야 바뀌기 전 값과 비교한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RobotCurrentState s
               set s.stateVersion = case
                       when s.connectionState <> com.amrproject.domain.entity.robot.ConnectionState.ONLINE
                         or (:batteryPct is not null and s.batteryPct <> :batteryPct)
                       then s.stateVersion + 1 else s.stateVersion end,
                   s.connectionState = com.amrproject.domain.entity.robot.ConnectionState.ONLINE,
                   s.batteryPct = coalesce(:batteryPct, s.batteryPct),
                   s.receivedAt = :now
             where s.robotId = :robotId
            """)
    int receiveTelemetry(Long robotId, Integer batteryPct, Instant now);
}
