package com.amrproject.robot;

import com.amrproject.domain.entity.RobotCommand;
import com.amrproject.domain.entity.RobotConfiguration;

/**
 * 서버가 로봇과 통신하는 경계(포트). 로봇 통신 규격이 아직 미정이라
 * (docs/DESIGN.md 미확정 항목, docs/API.md §7) 지금은 {@link SimulatorRobotGateway}만
 * 구현체로 둔다 — 실제 규격이 정해지면 같은 인터페이스의 다른 어댑터로 교체한다.
 */
public interface RobotGateway {

    /**
     * 이미 영속화된(ID가 있는) 명령을 로봇에 전달한다. 구현체는 명령의 상태를
     * 최종 상태(SUCCEEDED/FAILED/UNKNOWN)까지 전이시키고 CommandEvent를 기록한다.
     */
    void dispatch(RobotCommand command);

    /**
     * 로봇에 새 설정을 적용한다. 구현체는 성공 시 해당 로봇의 applied_config를
     * 이 설정으로 갱신해야 한다 (desired와 별도로 추적, docs/ERD.md §4.4).
     */
    void applyConfiguration(RobotConfiguration configuration);
}
