package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotCommandStatus;
import com.amrproject.domain.entity.robot.RobotCommandType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RobotCommandRepository extends JpaRepository<RobotCommand, Long> {

    /**
     * 39단계: 비동기 결과(MQTT state의 orderId = commandNo)로 명령 id만 찾는다. 엔티티를 먼저 읽어 두면 뒤의 잠금 조회가 영속성 컨텍스트에
     * 이미 있는 (잠그기 전) 값을 돌려주므로, 잠그기 전에는 id만 읽는다.
     */
    @Query("select c.id from RobotCommand c where c.commandNo = :commandNo")
    Optional<Long> findIdByCommandNo(String commandNo);

    /** 39단계: 명령이 속한 작업 id(작업 단계 명령이 아니면 없음) — 작업 행을 먼저 잠그기 위해 id만 읽는다. */
    @Query("select s.task.id from RobotCommand c join c.taskStep s where c.id = :commandId")
    Optional<Long> findTaskIdByCommandId(Long commandId);

    /** 39단계: 결과 반영·발행 확인이 같은 명령을 동시에 바꾸지 않도록 행을 잠근다. 잠금 조회는 최신 커밋 값을 읽는다(31단계 참고). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RobotCommand c where c.id = :id")
    Optional<RobotCommand> findByIdForUpdate(Long id);

    /** 39단계: 보낸 지 오래됐는데 아직 결과가 없는 명령(타임아웃 감시). */
    @Query("select c.id from RobotCommand c where c.status in :statuses and c.commandType in :types and c.sentAt < :sentBefore")
    List<Long> findIdsInFlightSentBefore(Collection<RobotCommandStatus> statuses, Collection<RobotCommandType> types, Instant sentBefore);

    /** 39단계: 단계의 가장 최근 명령 — 복구 재개로 새 명령이 나간 뒤 도착한 옛 명령의 결과를 걸러낸다. */
    Optional<RobotCommand> findFirstByTaskStepIdOrderByIdDesc(Long taskStepId);
}
