package com.amrproject.repository.robot;

import com.amrproject.domain.entity.robot.ChargingSession;
import com.amrproject.domain.entity.robot.ChargingSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChargingSessionRepository extends JpaRepository<ChargingSession, Long> {

    /** 39단계: 충전 명령 결과가 비동기로 오면 그 작업의 진행 중 충전 세션을 마무리한다. */
    Optional<ChargingSession> findFirstByTaskIdAndStatus(Long taskId, ChargingSessionStatus status);
}
