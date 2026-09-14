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
        List<String> errors,
        BatteryState batteryState,
        String lastNodeId,
        /** AUTOMATIC, SEMIAUTOMATIC, MANUAL, SERVICE, TEACHIN */
        String operatingMode,
        SafetyState safetyState,
        List<ActionState> actionStates
) {

    /** 18단계 필드만 쓰는 기존 메시지(테스트·가상 로봇)용. */
    public Vda5050State(String orderId, Boolean driving, List<String> errors, BatteryState batteryState) {
        this(orderId, driving, errors, batteryState, null, null, null, null);
    }

    public record BatteryState(Double batteryCharge) {
    }

    /** eStop: AUTOACK, MANUAL, REMOTE, NONE */
    public record SafetyState(String eStop, Boolean fieldViolation) {
    }

    /** actionStatus: WAITING, INITIALIZING, RUNNING, PAUSED, FINISHED, FAILED */
    public record ActionState(String actionId, String actionType, String actionStatus) {
    }
}
