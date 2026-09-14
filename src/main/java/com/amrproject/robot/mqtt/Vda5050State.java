package com.amrproject.robot.mqtt;

import java.util.List;

/**
 * VDA 5050 "state" 메시지의 축소 구현 (docs/API.md §7 참고). driving:false 또는 errors가 있으면 주문이 종료된 것으로 본다.
 *
 * <p>41단계: 동작 모드(operatingMode), 안전 상태(safetyState), 마지막 노드(lastNodeId), 즉시 동작 결과(actionStates)도 읽는다.
 * 그 밖의 필드(agvPosition, loads 등)는 받아도 무시한다.
 */
public record Vda5050State(
        String orderId,
        Boolean driving,
        /** 43단계: 스펙대로 객체 목록(예전엔 문자열 목록이라 실제 로봇의 오류 보고가 통째로 파싱 실패했다) */
        List<Error> errors,
        BatteryState batteryState,
        String lastNodeId,
        /** AUTOMATIC, SEMIAUTOMATIC, MANUAL, SERVICE, TEACHIN */
        String operatingMode,
        SafetyState safetyState,
        List<ActionState> actionStates,
        /** 43단계: 아직 지나야 할 주문 노드. 비어 있어야 주문이 끝난 것이다 */
        List<NodeState> nodeStates,
        /** 43단계: startPause로 일시정지(정지) 중 */
        Boolean paused
) {

    /** 18단계 필드만 쓰는 기존 메시지(테스트·가상 로봇)용. */
    public Vda5050State(String orderId, Boolean driving, List<Error> errors, BatteryState batteryState) {
        this(orderId, driving, errors, batteryState, null, null, null, null, null, null);
    }

    public record BatteryState(Double batteryCharge) {
    }

    /** errorLevel: WARNING, FATAL */
    public record Error(String errorType, String errorLevel, String errorDescription) {
    }

    /** eStop: AUTOACK, MANUAL, REMOTE, NONE */
    public record SafetyState(String eStop, Boolean fieldViolation) {
    }

    /** actionStatus: WAITING, INITIALIZING, RUNNING, PAUSED, FINISHED, FAILED */
    public record ActionState(String actionId, String actionType, String actionStatus) {
    }

    public record NodeState(String nodeId, Integer sequenceId, Boolean released) {
    }

    /**
     * 43단계: 주문이 끝났는가. 오류가 있으면 끝(실패)이다. 아니면 멈춰 있고(driving=false), 지날 노드(nodeStates)가 남지 않았고,
     * 진행 중인 동작(actionStates가 FINISHED/FAILED가 아닌 것)이 없어야 끝(성공)이다. 예전엔 driving=false만 봐서 주문을 막 받고 출발하기 전이나
     * 충전 중(멈춰 있음)에도 끝난 것으로 판단했다. 보고에 없는 필드는 조건에서 뺀다(축소 구현 로봇 호환).
     */
    public boolean orderFinished() {
        if (hasErrors()) {
            return true;
        }
        boolean nodesLeft = nodeStates != null && !nodeStates.isEmpty();
        boolean actionsRunning = actionStates != null && actionStates.stream()
                .anyMatch(action -> !"FINISHED".equals(action.actionStatus()) && !"FAILED".equals(action.actionStatus()));
        return Boolean.FALSE.equals(driving) && !nodesLeft && !actionsRunning;
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
