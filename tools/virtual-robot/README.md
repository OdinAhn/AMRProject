# VDA 5050 가상 로봇 (43단계)

실제 로봇 없이 서버의 **MQTT/VDA 5050 모드**를 끝까지 돌려 보기 위한 Python 도구입니다. 실제 로봇 연동을 준비할 때
서버가 스펙 형식의 메시지를 제대로 주고받는지, 비동기 명령·충전·비상정지·통신 두절이 운영 흐름대로 처리되는지 확인합니다.

서버 안의 시뮬레이터(`SimulatorRobotGateway`, 기본 모드)와 달리, 이 가상 로봇은 **별도 프로세스**로 MQTT 브로커에 접속하고 실제 로봇처럼
시간을 들여 움직입니다. 서버 입장에서는 실제 로봇과 구분되지 않습니다.

| 파일 | 역할 |
|---|---|
| `virtual_robot.py` | 가상 로봇 무리 실행(로봇마다 MQTT 연결, 콘솔 명령) |
| `robot.py` | 로봇 한 대의 동작 로직(통신과 분리, 단위 테스트 대상) |
| `route_map.py` | 활성 지도 그래프·최단 경로(서버 `MapRouteService`와 같은 규칙) |
| `seed.py` | 시연 데이터(물류센터·3×3 격자 지도·작업장·로봇) 넣기 |
| `task.py` | 작업 호출·진행(작업자 화면 대신) |
| `server_api.py` | 서버 REST API 클라이언트(표준 라이브러리) |
| `tests/` | 단위 테스트(브로커·서버 불필요) |

## 준비

```bash
cd tools/virtual-robot
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt     # macOS/Linux: .venv/bin/python
```

Python 3.11 이상, 의존성은 `paho-mqtt`뿐입니다. 서버 API를 쓰는 스크립트는 관리자 계정을 환경 변수로 받습니다.

```bash
export AMR_API_URL=http://localhost:8080          # 기본값
export AMR_API_USERNAME=admin AMR_API_PASSWORD=...  # 또는 AMR_API_TOKEN=...
```

## 운영 브로커(인증·TLS)에 붙이기 (44단계)

운영 compose의 브로커는 익명 접속을 막고 로봇마다 계정(이름 = 로봇 코드)을 요구하며, 로봇은 TLS(8883)로 접속합니다(docs/DEPLOYMENT.md §4-1).

```bash
.venv/Scripts/python virtual_robot.py --robots VR-01,VR-02,VR-03 \
  --broker ssl://<브로커 호스트>:8883 \
  --credentials ../../infra/mosquitto/robot-credentials \
  --tls-ca ../../infra/mosquitto/certs/ca.crt
```

- `--credentials`: 브로커와 같은 `시리얼번호:비밀번호` 파일(로봇별 계정). 모든 로봇이 같은 비밀번호면 `--password`만 줘도 됩니다(계정 이름은 로봇 코드).
- `--tls-ca`: 브로커 인증서를 검증할 CA. `ssl://` 주소면 TLS로 접속합니다.
- 개발용 브로커(`docker-compose.yml`, 인증 없음)에는 이 옵션 없이 `tcp://localhost:1883`으로 붙습니다.

## 처음부터 끝까지

```bash
# 1. 인프라(MySQL·Redis·Kafka·Mosquitto)
docker compose up -d

# 2. 서버를 MQTT 모드로 (저장소 루트에서)
SPRING_PROFILES_ACTIVE=local ROBOT_GATEWAY_MQTT_ENABLED=true ./gradlew bootRun

# 3. 시연 데이터 (한 번만 — 같은 물류센터 코드가 있으면 아무것도 안 함)
.venv/Scripts/python seed.py                 # SITE-VR, 노드 A1~C3, IN-1/IN-2/OUT-1/OUT-2/CHG-1, 대기 구역 SHELF-W(A2)/SHELF-N(B1)/SHELF-E(C2), 로봇 VR-01~03

# 4. 가상 로봇 실행 (콘솔 명령을 받음, Ctrl+C로 종료)
.venv/Scripts/python virtual_robot.py --robots VR-01,VR-02,VR-03

# 5. 다른 터미널에서 운반 작업 한 건을 끝까지 (자동 배정 → 이동 → 보내기 → 이동 → 하역 완료)
.venv/Scripts/python task.py run --from IN-1 --to OUT-1
```

관리자 화면 `/dashboard`에서 물류센터 "가상 로봇 시연 센터"를 고르면 로봇이 지도 노드를 따라 위치가 바뀌고, 로봇 상태표·작업·알람이 갱신됩니다.

## 가상 로봇의 동작

| 서버가 보냄 | 가상 로봇 |
|---|---|
| `order` (46단계부터 서버가 예약한 경로 전체 — 여러 노드면 그 노드를 차례로 따라감) | 노드가 하나면 현재 노드에서 활성 지도 최단 경로로 `--speed`(기본 20/초)로 이동. 이동 중 `driving:true`, `nodeStates`에 남은 노드, 노드를 지날 때 `lastNodeId` 갱신. 도착하면 `driving:false`·`nodeStates:[]` |
| 목적지 노드의 `startCharging` 동작 | 도착 뒤 배터리가 100%가 될 때까지 `actionStates` RUNNING·`charging:true`(`--charge-per-sec`) → FINISHED |
| `instantActions` `startPause` (서버 비상정지) | 즉시 정지, `paused:true`, `eStop:REMOTE`로 **현장 해제 전까지 유지**(서버 정책: 비상정지 해제는 현장) |
| `instantActions` `stopPause` | 원격 비상정지 중이 아니면 재개, 아니면 FAILED |
| `instantActions` `cancelOrder` | 그 자리에서 멈추고 주문을 비움 |
| 주문 거부 | 비상정지·일시정지·수동 모드면 `orderError`, 갈 길이 없으면 `noRouteError`(스펙 형식 `errors` 객체) |

- 1초(`--state-interval`)마다, 그리고 주문·즉시 동작을 받은 즉시 `state`를 보냅니다. 서버는 이것을 하트비트로도 씁니다.
- `connection` 토픽에 `ONLINE`(retain)을 보내고, 비정상 종료 시 브로커가 `CONNECTIONBROKEN`(LWT)을 알립니다. 서버는 이를 받아 바로 OFFLINE으로 바꿉니다(45단계) — 프로세스를 강제 종료해 확인할 수 있습니다.
- 배터리는 이동 거리 1당 `--drain-per-unit`(기본 0.05%) 줄어듭니다. 서버 기준(기본 15%) 이하가 되면 서버가 충전 주문을 보냅니다.
- 시작 위치: `--start-nodes C1,B2,A3` → 없으면 서버가 알고 있는 로봇 현재 노드 → 없으면 지도 노드를 차례로.
- 지도가 없거나 목적지가 지도에 없는 노드면(`--no-api` 포함) 위치 없이 `--travel-seconds`(기본 5초) 뒤 도착한 것으로 보고합니다.

## 콘솔 명령 (장애 흉내)

`virtual_robot.py` 실행 중 표준 입력으로 입력합니다(`R`은 로봇 코드 또는 `all`).

| 명령 | 흉내 내는 상황 | 서버에서 볼 결과 |
|---|---|---|
| `status` | — | 로봇별 위치·배터리·주문·플래그 출력 |
| `estop R` / `release R` | 현장 비상정지 버튼 / 현장 해제 | 안전 상태 비상정지·`ROBOT_EMERGENCY_STOP` 알람·배정 제외 / 해소 |
| `manual R` / `auto R` | 현장에서 수동 모드로 전환 / 자동 복귀 | 동작 모드 MANUAL·자동 배정 제외 / AUTO |
| `obstacle R` | 장애물 감지(보호 필드 침범) 켜기·끄기 | 보호 정지, 이동 멈춤 |
| `lost R` | 위치 추정 실패 켜기·끄기(`positionInitialized:false`, 주문 거부) | 위치 추정 LOST·`LOCALIZATION_LOST` 알람·배정 제외 / 해소 |
| `load R` / `unload R` | 짐 싣기·내리기(`loads`) | 적재 상태 LOADED / EMPTY |
| `silent R` / `unsilent R` | 통신 두절(주문 무시·state 안 보냄) / 복구 | 하트비트 시간(기본 30초) 뒤 OFFLINE·`ROBOT_COMM_LOST`, 진행 중 명령은 `MQTT_COMMAND_TIMEOUT_SECONDS` 뒤 UNKNOWN·복구 필요 / ONLINE·늦은 도착 보고로 자동 재개(40단계) |
| `fail R` | 다음 주문을 경로 없음으로 거부 | 명령 FAILED·작업 복구 필요 |
| `battery R 10` | 배터리 급감 | 다음 디스패치에서 가까운 충전소로 충전 주문 |

백그라운드에서 돌릴 때는 `--no-console`을 붙이거나, 파일을 표준 입력으로 흘려 명령을 넣을 수 있습니다: `tail -f cmds.txt | python virtual_robot.py ...`

## 테스트

```bash
.venv/Scripts/python -m unittest discover -s tests
```

CI(`ci.yml`의 `virtual-robot` job)에서도 실행합니다. 실제 브로커·서버를 붙인 전체 흐름은 `docs/DESIGN.md` §11 43단계 검증 기록을 참고하세요.

## 실제 로봇과의 차이 · 연동 전 확인할 것

- 서버의 VDA 5050 구현은 **축소 버전**입니다. 46단계부터 주문에 교통 관리로 예약한 경로 전체(`nodes`·`edges`, 모두 `released:true`)를 담지만, 노드 좌표·edge 속성(최대 속도·궤적)·부분 해제는 없습니다. 제조사가 이것들을 요구하면 서버 쪽 확장이 필요합니다(`app.robot-gateway.mqtt.full-route=false`면 목적지 노드 하나).
- 서버는 경로의 노드 사이를 로봇이 알아서 간다고 가정합니다 — 주문의 연속한 두 노드가 로봇 지도에서도 직접 이어져 있어야 합니다.
- 47단계부터 서버가 작업 없이 "길 비키기" 주문(경로 전체, 목적지는 마지막 노드)을 보낼 수 있습니다. 가상 로봇에게는 일반 주문과 같습니다. 서버는 로봇이 노드를 지날 때 보내는 `lastNodeId`로 지나간 노드 예약을 풀므로, 실제 로봇도 노드를 지날 때마다 `lastNodeId`를 갱신해 보고해야 뒤따르는 로봇이 덜 기다립니다.
- 48단계: 할 일 없는 로봇은 마지막 작업이 끝나고 30초(`app.parking.idle-seconds`) 뒤 대기 구역(PARKING)으로 가는 주문을 받습니다(구역마다 고르게). 배터리가 충전 기준 이하면 먼저 충전소로 갑니다 — `--battery 12`로 띄우면 충전 후 대기 구역으로 가는 흐름을 볼 수 있습니다. 48단계 전에 만든 시연 물류센터에는 대기 구역이 없으니 `--site-code`를 바꿔 새로 넣으세요.
- 서버는 `agvPosition` 좌표를 쓰지 않고 `lastNodeId`로만 위치를 압니다 — 로봇이 보고하는 노드 id가 서버 지도 노드 코드와 같아야 합니다.
- 비상정지는 표준에 원격 비상정지 동작이 없어 `startPause`(HARD)로 보냅니다. 실제 로봇이 이것을 어떻게 다루는지(유지·자동 해제)는 제조사별로 확인해야 합니다.
- 서버는 `loads`는 빈 차/적재 여부만, `agvPosition`은 위치 추정 여부만 봅니다(좌표·짐 종류는 무시).
