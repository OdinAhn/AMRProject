package com.amrproject.robot.mqtt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 43단계: VDA 5050 주문 종료 판단. 멈춰 있는 것(driving=false)만으로는 끝이 아니다 — 지날 노드가 남았거나(출발 전) 동작이 진행 중이면
 * (충전 중) 아직 진행 중이다. 오류가 있으면 곧바로 끝(실패)이다. 보고에 없는 필드는 조건에서 빠진다(18단계 축소 로봇 호환).
 */
class Vda5050StateTest {

    @Test
    void 멈춰_있어도_지날_노드나_진행_중인_동작이_남아_있으면_끝나지_않았다() {
        List<Vda5050State.NodeState> pendingNode = List.of(new Vda5050State.NodeState("N2", 0, true));
        List<Vda5050State.ActionState> charging = List.of(new Vda5050State.ActionState("order-1", "startCharging", "RUNNING"));
        List<Vda5050State.ActionState> charged = List.of(new Vda5050State.ActionState("order-1", "startCharging", "FINISHED"));

        assertThat(state(false, null, pendingNode, null).orderFinished()).isFalse();
        assertThat(state(false, null, List.of(), charging).orderFinished()).isFalse();
        assertThat(state(true, null, List.of(), null).orderFinished()).isFalse();

        assertThat(state(false, null, List.of(), charged).orderFinished()).isTrue();
        assertThat(state(false, null, null, null).orderFinished()).isTrue();
    }

    @Test
    void 오류가_있으면_남은_노드와_관계없이_끝났다() {
        List<Vda5050State.Error> errors = List.of(new Vda5050State.Error("noRouteError", "FATAL", "경로 없음"));
        Vda5050State state = state(true, errors, List.of(new Vda5050State.NodeState("N2", 0, true)), null);

        assertThat(state.hasErrors()).isTrue();
        assertThat(state.orderFinished()).isTrue();
    }

    private static Vda5050State state(
            Boolean driving, List<Vda5050State.Error> errors, List<Vda5050State.NodeState> nodeStates, List<Vda5050State.ActionState> actionStates
    ) {
        return new Vda5050State("order-1", driving, errors, null, null, null, null, actionStates, nodeStates, null);
    }
}
