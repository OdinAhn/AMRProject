package com.amrproject.robot;

import com.amrproject.domain.entity.RobotCommand;

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
}
