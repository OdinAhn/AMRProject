package com.amrproject.robot.mqtt;

import java.util.List;

/**
 * VDA 5050 "state" 메시지의 축소 구현 — orderId/driving/errors/batteryState만 본다
 * (docs/API.md §7 참고). driving:false 또는 errors가 있으면 주문이 종료된 것으로 본다.
 */
public record Vda5050State(
        String orderId,
        Boolean driving,
        List<String> errors,
        BatteryState batteryState
) {
    public record BatteryState(Double batteryCharge) {
    }
}
