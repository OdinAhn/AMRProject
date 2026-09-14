"""VDA 5050 가상 로봇 무리를 MQTT 브로커에 붙여 실행한다 — 실제 로봇 없이 서버의 MQTT 모드를 끝까지 돌려 보기 위한 도구.

사용법은 README.md 참고. 예:
    python virtual_robot.py --robots VR-01,VR-02 --api-url http://localhost:8080
    (관리자 계정은 환경 변수 AMR_API_USERNAME / AMR_API_PASSWORD, 또는 AMR_API_TOKEN)

로봇마다 MQTT 연결을 따로 맺고(연결 끊김 알림 LWT 포함) 다음 토픽을 쓴다(서버 MqttRobotGateway와 같음):
    구독  uagv/v2/{manufacturer}/{serial}/order, .../instantActions
    발행  uagv/v2/{manufacturer}/{serial}/state (주기 + 변화 즉시), .../connection
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import threading
import time
from datetime import datetime, timezone
from urllib.parse import urlparse

import paho.mqtt.client as mqtt

from robot import RobotConfig, VirtualRobot
from route_map import RouteMap
from server_api import ApiError, ServerApi

TOPIC_PREFIX = "uagv/v2"
log = logging.getLogger("virtual-robot")


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


class RobotConnection:
    """가상 로봇 한 대 + MQTT 연결."""

    def __init__(self, robot: VirtualRobot, broker: "BrokerSettings", lock: threading.Lock):
        self.robot = robot
        self.lock = lock
        self.header_id = 0
        base = f"{TOPIC_PREFIX}/{robot.manufacturer}/{robot.serial}"
        self.topic_order, self.topic_instant = f"{base}/order", f"{base}/instantActions"
        self.topic_state, self.topic_connection = f"{base}/state", f"{base}/connection"
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"virtual-{robot.serial}")
        # 44단계: 운영 브로커는 로봇마다 계정(이름 = 시리얼번호)과 TLS를 요구한다
        password = broker.password_for(robot.serial)
        if password is not None:
            self.client.username_pw_set(robot.serial if broker.username is None else broker.username, password)
        if broker.tls:
            self.client.tls_set(ca_certs=broker.ca_file)
        self.client.will_set(self.topic_connection, json.dumps(self._connection("CONNECTIONBROKEN")), qos=1, retain=True)
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.client.connect(broker.host, broker.port, keepalive=30)

    def start(self) -> None:
        self.client.loop_start()

    def stop(self) -> None:
        self.client.publish(self.topic_connection, json.dumps(self._connection("OFFLINE")), qos=1, retain=True)
        self.client.loop_stop()
        self.client.disconnect()

    def publish_state(self) -> None:
        with self.lock:
            if self.robot.silent:
                return
            self.header_id += 1
            message = self.robot.state_message(self.header_id, now_iso())
        self.client.publish(self.topic_state, json.dumps(message), qos=0)

    def _on_connect(self, client, userdata, flags, reason_code, properties) -> None:
        client.subscribe([(self.topic_order, 1), (self.topic_instant, 1)])
        client.publish(self.topic_connection, json.dumps(self._connection("ONLINE")), qos=1, retain=True)
        log.info("%s 브로커 연결됨", self.robot.serial)

    def _on_message(self, client, userdata, message) -> None:
        try:
            payload = json.loads(message.payload.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            log.warning("%s 해석할 수 없는 메시지 무시: %s", self.robot.serial, message.topic)
            return
        with self.lock:
            if message.topic == self.topic_order:
                accepted = self.robot.handle_order(payload)
                if accepted:
                    targets = [node.get("nodeId") for node in payload.get("nodes") or []]
                    outcome = "거부: " + self.robot.errors[0]["errorDescription"] if self.robot.errors else "수락"
                    log.info("%s 주문 %s → %s (%s)", self.robot.serial, payload.get("orderId", "")[:8], targets, outcome)
            else:
                self.robot.handle_instant_actions(payload)
                log.info("%s 즉시 동작 %s", self.robot.serial, [a.get("actionType") for a in payload.get("actions") or []])
        self.publish_state()  # 받은 결과를 주기를 기다리지 않고 바로 알린다

    def _connection(self, state: str) -> dict:
        self.header_id += 1
        return {"headerId": self.header_id, "timestamp": now_iso(), "version": "2.0.0",
                "manufacturer": self.robot.manufacturer, "serialNumber": self.robot.serial, "connectionState": state}


class BrokerSettings:
    """브로커 주소·계정·TLS. ssl:// 또는 mqtts:// 주소면 TLS(--tls-ca로 신뢰할 CA, 없으면 시스템 신뢰 저장소)."""

    def __init__(self, url: str, username: str | None, password: str | None, credentials_file: str | None, ca_file: str | None):
        parsed = urlparse(url)
        self.tls = parsed.scheme in ("ssl", "mqtts", "tls")
        self.host = parsed.hostname or "localhost"
        self.port = parsed.port or (8883 if self.tls else 1883)
        self.username = username
        self.password = password
        self.ca_file = ca_file
        self.credentials: dict[str, str] = {}
        if credentials_file:
            with open(credentials_file, encoding="utf-8") as file:
                for line in file:
                    line = line.strip()
                    if line and not line.startswith("#") and ":" in line:
                        serial, secret = line.split(":", 1)
                        self.credentials[serial.strip()] = secret.strip()

    def password_for(self, serial: str) -> str | None:
        """로봇별 계정 파일(시리얼번호:비밀번호)이 우선, 없으면 공통 --password. 둘 다 없으면 익명(개발용 브로커)."""
        return self.credentials.get(serial, self.password)


def load_map_and_positions(api: ServerApi, codes: list[str]) -> tuple[RouteMap | None, dict[str, str]]:
    """로봇들이 속한 물류센터의 활성 지도와, 서버가 알고 있는 로봇 현재 노드(코드)를 가져온다."""
    robots = {robot["code"]: robot for robot in api.get("/api/admin/robots")}
    missing = [code for code in codes if code not in robots]
    if missing:
        log.warning("서버에 등록되지 않은 로봇: %s — 서버가 이 로봇에 명령을 보내지 않습니다(seed.py 또는 관리자 화면에서 등록)", missing)
    site_ids = {robots[code]["siteId"] for code in codes if code in robots}
    if len(site_ids) != 1:
        log.warning("로봇들의 물류센터가 하나가 아닙니다(%s) — 지도 없이 시간 기반으로 이동합니다", site_ids or "없음")
        return None, {}
    site_id = site_ids.pop()
    overview = api.get(f"/api/admin/sites/{site_id}/overview")
    version_id = overview.get("activeMapVersionId")
    if version_id is None:
        log.warning("물류센터 %s에 활성 지도가 없습니다 — 지도 없이 시간 기반으로 이동합니다", site_id)
        return None, {}
    nodes = api.get(f"/api/admin/map-versions/{version_id}/nodes")
    route_map = RouteMap.from_api(version_id, nodes, api.get(f"/api/admin/map-versions/{version_id}/edges"))
    code_by_id = {node["id"]: node["code"] for node in nodes}
    positions = {robot["code"]: code_by_id[robot["currentNodeId"]]
                 for robot in overview.get("robots", []) if robot.get("currentNodeId") in code_by_id}
    log.info("활성 지도 버전 %s: 노드 %d개", version_id, len(route_map.nodes))
    return route_map, positions


def console(connections: dict[str, RobotConnection], lock: threading.Lock, stop: threading.Event) -> None:
    help_text = """명령: status | estop R | release R | manual R | auto R | obstacle R | silent R | unsilent R | fail R | battery R 값 | quit
  (R은 로봇 코드 또는 all) estop: 비상정지 버튼, release: 현장 해제(비상정지·일시정지), manual/auto: 동작 모드,
  obstacle: 장애물 감지 켜기/끄기, silent/unsilent: 통신 두절 흉내, fail: 다음 주문을 경로 없음으로 거부, battery: 배터리(%) 설정"""
    print(help_text, flush=True)
    for line in sys.stdin:
        parts = line.split()
        if not parts:
            continue
        command, targets = parts[0].lower(), parts[1] if len(parts) > 1 else "all"
        if command in ("quit", "exit"):
            stop.set()
            return
        if command == "status":
            with lock:
                for connection in connections.values():
                    print(connection.robot.summary(), flush=True)
            continue
        if command == "help":
            print(help_text, flush=True)
            continue
        selected = list(connections.values()) if targets == "all" else [connections[targets]] if targets in connections else []
        if not selected:
            print(f"모르는 로봇: {targets}", flush=True)
            continue
        with lock:
            for connection in selected:
                robot = connection.robot
                if command == "estop":
                    robot.press_emergency_stop()
                elif command == "release":
                    robot.release_emergency_stop()
                elif command == "manual":
                    robot.operating_mode = "MANUAL"
                elif command == "auto":
                    robot.operating_mode = "AUTOMATIC"
                elif command == "obstacle":
                    robot.field_violation = not robot.field_violation
                elif command == "silent":
                    robot.silent = True
                elif command == "unsilent":
                    robot.silent = False
                elif command == "fail":
                    robot.fail_next_order = True
                elif command == "battery" and len(parts) > 2:
                    robot.battery = max(0.0, min(100.0, float(parts[2])))
                else:
                    print(f"모르는 명령: {line.strip()} (help)", flush=True)
                    break
                print(robot.summary(), flush=True)
        for connection in selected:
            connection.publish_state()


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="VDA 5050 가상 로봇 무리")
    parser.add_argument("--broker", default=os.environ.get("MQTT_BROKER_URL", "tcp://localhost:1883"), help="MQTT 브로커 (tcp://host:port, TLS는 ssl://host:8883)")
    parser.add_argument("--credentials", default=os.environ.get("MQTT_ROBOT_CREDENTIALS"), help="로봇 계정 파일(시리얼번호:비밀번호, 브로커의 robot-credentials와 같은 형식)")
    parser.add_argument("--username", default=None, help="모든 로봇이 같은 계정을 쓸 때만(기본은 로봇 코드가 계정 이름)")
    parser.add_argument("--password", default=os.environ.get("MQTT_ROBOT_PASSWORD"), help="모든 로봇 공통 비밀번호(로봇별 파일이 우선)")
    parser.add_argument("--tls-ca", default=os.environ.get("MQTT_TLS_CA_FILE"), help="TLS 서버 인증서를 검증할 CA(PEM)")
    parser.add_argument("--manufacturer", default=os.environ.get("MQTT_MANUFACTURER", "AMRProject"))
    parser.add_argument("--robots", default="VR-01,VR-02,VR-03", help="로봇 코드 목록(쉼표) — 서버에 등록된 코드와 같아야 한다")
    parser.add_argument("--api-url", default=os.environ.get("AMR_API_URL", "http://localhost:8080"), help="활성 지도를 읽을 서버 주소")
    parser.add_argument("--no-api", action="store_true", help="서버 API를 쓰지 않는다(지도 없이 시간 기반 이동)")
    parser.add_argument("--start-nodes", default="", help="로봇별 시작 노드 코드(쉼표). 없으면 서버가 아는 위치, 그것도 없으면 지도 노드를 차례로")
    parser.add_argument("--battery", type=float, default=100.0)
    parser.add_argument("--speed", type=float, default=RobotConfig.speed)
    parser.add_argument("--drain-per-unit", type=float, default=RobotConfig.drain_per_unit)
    parser.add_argument("--charge-per-sec", type=float, default=RobotConfig.charge_per_sec)
    parser.add_argument("--travel-seconds", type=float, default=RobotConfig.travel_seconds)
    parser.add_argument("--state-interval", type=float, default=1.0, help="state 발행 주기(초)")
    parser.add_argument("--tick", type=float, default=0.1, help="시뮬레이션 간격(초)")
    parser.add_argument("--no-console", action="store_true", help="표준 입력 명령을 받지 않는다(백그라운드 실행)")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s", datefmt="%H:%M:%S")
    args = parse_args(argv)
    codes = [code.strip() for code in args.robots.split(",") if code.strip()]
    broker = BrokerSettings(args.broker, args.username, args.password, args.credentials, args.tls_ca)

    route_map, known_positions = None, {}
    if not args.no_api:
        api = ServerApi(args.api_url, os.environ.get("AMR_API_TOKEN"))
        try:
            if not api.token:
                username, password = os.environ.get("AMR_API_USERNAME"), os.environ.get("AMR_API_PASSWORD")
                if not username or not password:
                    log.error("AMR_API_USERNAME/AMR_API_PASSWORD(또는 AMR_API_TOKEN)가 필요합니다. 지도 없이 돌리려면 --no-api")
                    return 2
                api.login(username, password)
            route_map, known_positions = load_map_and_positions(api, codes)
        except (ApiError, OSError) as error:
            log.error("서버 API 호출 실패: %s", error)
            return 2

    start_nodes = [code.strip() for code in args.start_nodes.split(",") if code.strip()]
    fallback_nodes = sorted(route_map.nodes) if route_map else []
    config = RobotConfig(speed=args.speed, drain_per_unit=args.drain_per_unit, charge_per_sec=args.charge_per_sec,
                         travel_seconds=args.travel_seconds)
    lock = threading.Lock()
    connections: dict[str, RobotConnection] = {}
    for index, code in enumerate(codes):
        start = (start_nodes[index] if index < len(start_nodes)
                 else known_positions.get(code) or (fallback_nodes[index % len(fallback_nodes)] if fallback_nodes else None))
        robot = VirtualRobot(code, args.manufacturer, route_map, start, args.battery, config)
        connections[code] = RobotConnection(robot, broker, lock)
        log.info("%s 시작 위치 %s, 배터리 %.0f%%", code, start or "모름", args.battery)
    for connection in connections.values():
        connection.start()

    stop = threading.Event()
    if not args.no_console:
        threading.Thread(target=console, args=(connections, lock, stop), daemon=True).start()
    last_state = 0.0
    last_tick = time.monotonic()
    try:
        while not stop.is_set():
            time.sleep(args.tick)
            now = time.monotonic()
            with lock:
                for connection in connections.values():
                    connection.robot.tick(now - last_tick)
            last_tick = now
            if now - last_state >= args.state_interval:
                last_state = now
                for connection in connections.values():
                    connection.publish_state()
    except KeyboardInterrupt:
        pass
    finally:
        for connection in connections.values():
            connection.stop()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
