"""VDA 5050 가상 로봇 한 대의 동작(통신과 분리된 순수 로직).

서버가 보낸 order/instantActions를 받아 시간(tick)에 따라 통로를 따라 움직이고, 배터리를 쓰고, 충전하고, VDA 5050 state 메시지를 만든다.
MQTT 연결·스레드는 virtual_robot.py가 맡고, 이 모듈은 단위 테스트할 수 있게 시간과 입출력을 밖에서 받는다.

지원 범위(서버 축소 구현과 맞춤, docs/API.md §7):
- order: nodes의 마지막 노드를 목적지로 보고 최단 경로로 이동한다(서버는 목적지 노드 하나만 보낸다). 목적지 노드의 startCharging 동작은
  도착 후 배터리가 가득 찰 때까지 RUNNING이다.
- instantActions: startPause(즉시 정지), stopPause(재개), cancelOrder(주문 취소).
- 장애 흉내: 비상정지 버튼, 수동 모드, 장애물 감지(fieldViolation), 통신 두절(무응답), 다음 주문 실패.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

from route_map import RouteMap

FINAL_ACTION_STATUSES = {"FINISHED", "FAILED"}
MAX_INSTANT_ACTION_STATES = 20


@dataclass
class RobotConfig:
    speed: float = 20.0
    """지도 좌표 단위/초(서버 시뮬레이터 기본값과 같음)"""
    drain_per_unit: float = 0.05
    """이동 거리 1당 배터리 소모(%)"""
    idle_drain_per_sec: float = 0.0
    charge_per_sec: float = 5.0
    """충전 속도(%/초)"""
    travel_seconds: float = 5.0
    """지도를 모르거나 목적지 노드가 지도에 없을 때 이동에 걸리는 시간"""
    pause_latches_estop: bool = True
    """startPause를 원격 비상정지로 보고 eStop=REMOTE를 유지한다(서버 정책: 비상정지 해제는 현장에서만 — 콘솔 release로 해제)"""


@dataclass
class _ActionState:
    action_id: str
    action_type: str
    status: str = "WAITING"


@dataclass
class VirtualRobot:
    serial: str
    manufacturer: str
    route_map: RouteMap | None
    start_node: str | None = None
    battery: float = 100.0
    config: RobotConfig = field(default_factory=RobotConfig)

    def __post_init__(self) -> None:
        self.x, self.y, self.theta = 0.0, 0.0, 0.0
        self.last_node_id = ""
        if self.start_node:
            self._place_at(self.start_node)
        self.driving = False
        self.paused = False
        self.e_stop = "NONE"
        self.field_violation = False
        self.operating_mode = "AUTOMATIC"
        self.charging = False
        self.silent = False
        """통신 두절 흉내: 주문을 받지 않고 state도 보내지 않는다(움직임은 계속)"""
        self.fail_next_order = False
        self.position_lost = False
        """위치 추정 실패 흉내: positionInitialized=false, 주문 거부(45단계)"""
        self.loads: list[dict] = []
        """실린 짐(45단계). 콘솔 load/unload로 바꾼다"""
        self.order_id = ""
        self.order_update_id = 0
        self.errors: list[dict] = []
        self._node_states: list[dict] = []
        self._path: list[str] = []
        self._blind_remaining: float | None = None
        self._target: str | None = None
        self._order_actions: list[_ActionState] = []
        self._instant_actions: list[_ActionState] = []

    # ---- 서버에서 온 메시지 -------------------------------------------------------------------------------

    def handle_order(self, order: dict) -> bool:
        """주문을 받는다. 통신 두절 흉내 중이면 무시하고 False."""
        if self.silent:
            return False
        order_id = order.get("orderId", "")
        if order_id == self.order_id and order.get("orderUpdateId", 0) == self.order_update_id:
            return True  # 같은 주문 재수신
        nodes = sorted(order.get("nodes") or [], key=lambda node: node.get("sequenceId", 0))
        self._clear_order()
        self.order_id = order_id
        self.order_update_id = order.get("orderUpdateId", 0)
        self.errors = []
        if not nodes:
            return self._reject("orderError", "주문에 노드가 없습니다")
        if self.e_stop != "NONE" or self.paused:
            return self._reject("orderError", "비상정지·일시정지 중에는 주문을 받을 수 없습니다")
        if self.position_lost:
            return self._reject("orderError", "위치 추정에 실패해 주문을 받을 수 없습니다")
        if self.operating_mode != "AUTOMATIC":
            return self._reject("orderError", f"{self.operating_mode} 모드에서는 주문을 받을 수 없습니다")
        if self.fail_next_order:
            self.fail_next_order = False
            return self._reject("noRouteError", "장애 흉내: 경로를 찾지 못했습니다")

        target = nodes[-1]
        self._target = target["nodeId"]
        self._order_actions = [
            _ActionState(action["actionId"], action["actionType"]) for action in (target.get("actions") or [])
        ]
        if self.route_map and self.route_map.has(self.last_node_id) and self.route_map.has(self._target):
            path = self.route_map.shortest_path(self.last_node_id, self._target)
            if path is None:
                return self._reject("noRouteError", f"{self.last_node_id}에서 {self._target}로 가는 통로가 없습니다")
            self._path = path[1:]
            remaining = self._path or [self._target]
        else:
            # 지도나 현재 위치를 모르면 정해진 시간만큼 이동한 것으로 친다
            self._blind_remaining = self.config.travel_seconds
            remaining = [self._target]
        self._node_states = [{"nodeId": code, "sequenceId": index * 2, "released": True} for index, code in enumerate(remaining)]
        self.driving = True
        return True

    def handle_instant_actions(self, message: dict) -> None:
        if self.silent:
            return
        for action in message.get("actions") or []:
            state = _ActionState(action.get("actionId", ""), action.get("actionType", ""))
            if state.action_type == "startPause":
                self.paused = True
                self.driving = False
                if self.config.pause_latches_estop:
                    self.e_stop = "REMOTE"
                state.status = "FINISHED"
            elif state.action_type == "stopPause":
                if self.e_stop == "NONE":
                    self.paused = False
                    state.status = "FINISHED"
                else:
                    state.status = "FAILED"
            elif state.action_type == "cancelOrder":
                if self._order_active():
                    for order_action in self._order_actions:
                        if order_action.status not in FINAL_ACTION_STATUSES:
                            order_action.status = "FAILED"
                    self._path, self._node_states, self._blind_remaining = [], [], None
                    self.charging = False
                    self.driving = False
                    state.status = "FINISHED"
                else:
                    state.status = "FAILED"
            else:
                state.status = "FAILED"
            self._instant_actions.append(state)
        self._instant_actions = self._instant_actions[-MAX_INSTANT_ACTION_STATES:]

    # ---- 현장 조작(콘솔) --------------------------------------------------------------------------------

    def press_emergency_stop(self) -> None:
        self.e_stop = "MANUAL"
        self.driving = False

    def release_emergency_stop(self) -> None:
        self.e_stop = "NONE"
        self.paused = False

    # ---- 시간 진행 ---------------------------------------------------------------------------------------

    def tick(self, dt: float) -> None:
        blocked = self.paused or self.e_stop != "NONE" or self.field_violation
        if self._blind_remaining is not None and not blocked:
            self._blind_remaining -= dt
            if self._blind_remaining <= 0:
                self._blind_remaining = None
                self._arrive_blind()
        elif self._path and not blocked:
            self._move(self.config.speed * dt)
        self.driving = bool(self._path or self._blind_remaining is not None) and not blocked

        if self._order_active() and not self._path and self._blind_remaining is None:
            self._node_states = []
            self._run_order_actions(dt)
        if self.config.idle_drain_per_sec and not self.charging:
            self.battery = max(0.0, self.battery - self.config.idle_drain_per_sec * dt)

    def _move(self, budget: float) -> None:
        while budget > 0 and self._path:
            nxt = self.route_map.nodes[self._path[0]]
            dx, dy = nxt.x - self.x, nxt.y - self.y
            distance = math.hypot(dx, dy)
            if distance > 0:
                self.theta = math.atan2(dy, dx)
            step = min(budget, distance)
            if distance > 0:
                self.x += dx / distance * step
                self.y += dy / distance * step
            self.battery = max(0.0, self.battery - step * self.config.drain_per_unit)
            budget -= step
            if step >= distance:
                self.x, self.y = nxt.x, nxt.y
                self.last_node_id = nxt.code
                self._path.pop(0)
                if self._node_states and self._node_states[0]["nodeId"] == nxt.code:
                    self._node_states.pop(0)

    def _arrive_blind(self) -> None:
        if self._target:
            self._place_at(self._target)
            self.last_node_id = self._target

    def _run_order_actions(self, dt: float) -> None:
        for action in self._order_actions:
            if action.status in FINAL_ACTION_STATUSES:
                continue
            if action.action_type == "startCharging":
                action.status = "RUNNING"
                self.charging = True
                self.battery = min(100.0, self.battery + self.config.charge_per_sec * dt)
                if self.battery >= 100.0:
                    self.charging = False
                    action.status = "FINISHED"
            else:
                action.status = "FAILED"  # 이 가상 로봇이 모르는 동작
            return  # 동작은 하나씩 순서대로

    # ---- 보고 ------------------------------------------------------------------------------------------

    def state_message(self, header_id: int, timestamp: str) -> dict:
        return {
            "headerId": header_id,
            "timestamp": timestamp,
            "version": "2.0.0",
            "manufacturer": self.manufacturer,
            "serialNumber": self.serial,
            "orderId": self.order_id,
            "orderUpdateId": self.order_update_id,
            "lastNodeId": self.last_node_id,
            "lastNodeSequenceId": 0,
            "driving": self.driving,
            "paused": self.paused,
            "newBaseRequest": False,
            "operatingMode": self.operating_mode,
            "nodeStates": list(self._node_states),
            "edgeStates": [],
            "agvPosition": {
                "x": round(self.x, 3),
                "y": round(self.y, 3),
                "theta": round(self.theta, 3),
                "mapId": self.route_map.map_id if self.route_map else "unknown",
                "positionInitialized": bool(self.last_node_id) and not self.position_lost,
            },
            "loads": list(self.loads),
            "actionStates": [
                {"actionId": a.action_id, "actionType": a.action_type, "actionStatus": a.status}
                for a in self._order_actions + self._instant_actions
            ],
            "batteryState": {"batteryCharge": round(self.battery, 1), "charging": self.charging},
            "errors": list(self.errors),
            "information": [],
            "safetyState": {"eStop": self.e_stop, "fieldViolation": self.field_violation},
        }

    def summary(self) -> str:
        status = "이동" if self.driving else ("충전" if self.charging else "대기")
        flags = [flag for flag, on in (
            (f"eStop={self.e_stop}", self.e_stop != "NONE"),
            ("paused", self.paused),
            ("장애물", self.field_violation),
            ("통신두절", self.silent),
            ("다음주문실패", self.fail_next_order),
            ("위치추정실패", self.position_lost),
            (f"짐={len(self.loads)}", bool(self.loads)),
            (self.operating_mode, self.operating_mode != "AUTOMATIC"),
        ) if on]
        return (f"{self.serial}: {status} 위치={self.last_node_id or '?'} ({self.x:.1f},{self.y:.1f}) "
                f"배터리={self.battery:.1f}% 주문={self.order_id[:8] or '-'} {' '.join(flags)}").rstrip()

    # ---- 내부 -------------------------------------------------------------------------------------------

    def _order_active(self) -> bool:
        return bool(self._node_states or self._path or self._blind_remaining is not None
                    or any(a.status not in FINAL_ACTION_STATUSES for a in self._order_actions))

    def _clear_order(self) -> None:
        self._path, self._node_states, self._order_actions = [], [], []
        self._blind_remaining, self._target = None, None
        self.charging = False

    def _reject(self, error_type: str, description: str) -> bool:
        self._clear_order()
        self.driving = False
        self.errors = [{"errorType": error_type, "errorLevel": "WARNING", "errorDescription": description}]
        return True

    def _place_at(self, code: str) -> None:
        if self.route_map and self.route_map.has(code):
            node = self.route_map.nodes[code]
            self.x, self.y = node.x, node.y
        self.last_node_id = code
