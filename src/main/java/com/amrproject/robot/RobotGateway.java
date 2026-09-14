package com.amrproject.robot;

import com.amrproject.domain.entity.robot.RobotCommand;
import com.amrproject.domain.entity.robot.RobotConfiguration;

/**
 * 서버가 로봇과 통신하는 경계(포트). 로봇 통신 규격이 아직 미정이라
 * (docs/DESIGN.md 미확정 항목, docs/API.md §7) 지금은 {@link SimulatorRobotGateway}만
 * 구현체로 둔다 — 실제 규격이 정해지면 같은 인터페이스의 다른 어댑터로 교체한다.
 */
public interface RobotGateway {

    /**
     * 이미 영속화된(ID가 있는) 명령을 로봇에 전달한다. 호출자의 트랜잭션 안에서 부른다.
     *
     * <p>39단계: 구현체는 두 가지 중 하나로 동작한다.
     * <ul>
     *   <li>동기(시뮬레이터): 반환 전에 최종 상태(SUCCEEDED/FAILED/UNKNOWN)까지 전이시킨다. 호출자가 결과를 바로 작업에 반영한다.</li>
     *   <li>비동기(MQTT): SENT까지만 기록하고 반환한다(호출자는 단계를 RUNNING으로 둔다). 실제 발행은 커밋 뒤, 결과·타임아웃은
     *       {@code RobotCommandCompletionService}가 별도 트랜잭션으로 반영한다. 결과를 기다리며 호출자 트랜잭션(잠금)을 붙잡지 않는다.</li>
     * </ul>
     * 어느 쪽이든 모든 전이는 CommandEvent로 기록한다.
     */
    void dispatch(RobotCommand command);

    /**
     * 로봇에 새 설정을 적용한다. 구현체는 성공 시 해당 로봇의 applied_config를
     * 이 설정으로 갱신해야 한다 (desired와 별도로 추적, docs/ERD.md §4.4).
     */
    void applyConfiguration(RobotConfiguration configuration);
}
