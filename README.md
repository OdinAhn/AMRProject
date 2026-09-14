# AMRProject — AMR/AGV 관제 시스템

물류센터에서 작업 요청을 받아 로봇을 배정하고, 이동·적재·하역·충전을 관리하는 AMR/AGV(자율주행 이동로봇) 관제 시스템. 백엔드부터 검증한 뒤 관리자 화면, 작업자 화면 순으로 구현했다 (`docs/DESIGN.md` §11 실제 구현 순서 기준).

## 구성

| 영역 | 내용 |
|---|---|
| 백엔드 | Spring Boot 4 / Java 21, MySQL 8.4, Flyway, Spring Security, Redis(실시간 이벤트 팬아웃), Kafka(이벤트 로그) |
| 관리자 화면 | `frontend/` — React 19 + Vite + TypeScript, 지도·로봇·작업장·작업·디스패치·사용자·알람·실시간 이벤트 관리 |
| 작업자 화면 | 같은 프런트엔드 앱 내 `/worker` 라우트 — 로봇 호출, 보내기, 하역 완료, 작업 불가, 호출 취소 |
| 로봇 | 기본은 실제 장비 없이 통신 계약을 모사하는 가상 로봇(시뮬레이터). 옵트인으로 MQTT/VDA5050 어댑터도 지원 |

구현 범위와 각 단계별 완료 기준은 `docs/DESIGN.md` §11에, API 계약은 `docs/API.md`에, 상태 전이는 `docs/STATE_MACHINES.md`에, 테이블 구조는 `docs/ERD.md`에 정리되어 있다.

## 사전 준비

- JDK 21
- Docker (MySQL 실행 및 통합 테스트용 Testcontainers)
- Node.js 20+ (프론트엔드)

## 백엔드 실행

```bash
# MySQL + Redis + Kafka + Mosquitto(MQTT) 기동
docker compose up -d

# 애플리케이션 실행 (local 프로필: MySQL 8.4, localhost:3306)
SPRING_PROFILES_ACTIVE=local TOKEN_SIGNING_SECRET=<임의의-서명-키> ./gradlew bootRun
```

기본적으로 `http://localhost:8080`에서 API가 열린다. Swagger UI는 `/swagger-ui.html`, OpenAPI 스펙은 `/v3/api-docs`에서 확인할 수 있다.

주요 환경 변수(`application-local.yaml` 참고):

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3306` / `amr_project` | MySQL 접속 정보 |
| `DB_USERNAME` / `DB_PASSWORD` | `amr` / `amr` | MySQL 계정 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | 실시간 이벤트(SSE) 팬아웃용 Redis 접속 정보 |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | 이벤트 로그용 Kafka 브로커 접속 정보 |
| `ROBOT_GATEWAY_MQTT_ENABLED` | `false` | `true`면 로봇 통신을 MQTT/VDA5050 어댑터로 전환(기본은 시뮬레이터) |
| `MQTT_BROKER_URL` | `tcp://localhost:1883` | MQTT 브로커 접속 정보 (`ROBOT_GATEWAY_MQTT_ENABLED=true`일 때만 사용) |
| `MQTT_COMMAND_TIMEOUT_SECONDS` | `300` | (39단계) MQTT 로봇 명령을 보낸 뒤 결과가 없으면 UNKNOWN·작업 복구 필요로 보는 시간(초) |
| `TOKEN_SIGNING_SECRET` | (필수, 기본값 없음) | 액세스 토큰 서명용 비밀키 |
| `TOKEN_EXPIRY_MINUTES` | `480` | 액세스 토큰 만료 시간(분) |
| `EXTERNAL_API_KEY` | (운영 필수, local 프로필은 개발용 기본값) | 외부 시스템 API(`/api/external/**`)의 `X-API-Key` 값. 비어 있으면 외부 API는 전부 401 |
| `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` | (없음) | 둘 다 설정돼 있고 `ADMIN` 사용자가 없을 때만 기동 시 최초 관리자 계정을 만든다 |
| `AUTO_DISPATCH_ENABLED` / `AUTO_DISPATCH_INTERVAL_MS` | local: `true` / `5000` (기본 프로필은 꺼짐) | 운영 중 물류센터의 디스패치 사이클 자동 실행 여부와 주기 |
| `ROBOT_CONNECTION_MONITOR_ENABLED` | local: `true` (기본 프로필은 꺼짐) | 하트비트 기반 로봇 연결 감시(끊기면 OFFLINE·알람). 시뮬레이터 모드면 서버가 하트비트도 대신 친다 |
| `LOGIN_MAX_FAILURES_PER_USERNAME` / `LOGIN_MAX_FAILURES_PER_IP` | `5` / `50` | (36단계) 이 횟수만큼 로그인에 실패하면 아이디·IP를 잠근다 |
| `LOGIN_FAILURE_WINDOW_SECONDS` / `LOGIN_LOCK_SECONDS` | `900` / `900` | 실패를 세는 기간과 잠금 시간(초) |
| `ROBOT_HEARTBEAT_TIMEOUT_SECONDS` | `30` | 마지막 수신 후 이 시간이 지나면 OFFLINE으로 판정 |
| `app.retention.enabled` / `app.retention.days` / `app.retention.cron` | `true` / `30` / `0 30 3 * * *` | 발행 완료 outbox 이벤트·로봇 상태 이력 보관 기간과 삭제 시각(설정 파일 속성) |

처음 실행하는 빈 데이터베이스라면 최초 관리자 계정을 함께 만든다(관리자가 생긴 뒤에는 이 변수가 있어도 아무 일도 하지 않는다):

```bash
SPRING_PROFILES_ACTIVE=local BOOTSTRAP_ADMIN_USERNAME=admin BOOTSTRAP_ADMIN_PASSWORD=<비밀번호> ./gradlew bootRun
```

## 컨테이너로 전체 스택 실행 (35단계)

```bash
cp .env.example .env   # 비밀값 채우기
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

백엔드(`prod` 프로필)·nginx 프론트·MySQL·Redis·Kafka가 함께 뜨고, 화면은 `http://localhost`(`HTTP_PORT`)에서 열린다. 헬스체크·메트릭(`/actuator/prometheus`, 관리 포트 8081)·업데이트·백업은 `docs/DEPLOYMENT.md`를 참고.

## 테스트 · 빌드

```bash
./gradlew test    # Testcontainers로 실제 MySQL 컨테이너를 띄워 통합 테스트 실행 (Docker 필요)
./gradlew build
```

## 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev       # http://localhost:5173, /api 요청은 vite 프록시로 localhost:8080에 전달
```

```bash
npm run build      # 프로덕션 빌드 (tsc -b && vite build)
npm run lint        # oxlint
```

로그인 후 역할(`ADMIN`/`WORKER`)에 따라 관리자 화면(`/dashboard` 이하)과 작업자 화면(`/worker` 이하)으로 자동 이동한다.

## 데이터베이스 마이그레이션

Flyway 마이그레이션은 `src/main/resources/db/migration`에 있으며, 애플리케이션 기동 시 자동 적용된다. 새 마이그레이션 추가 시 기존 파일은 수정하지 않고 다음 버전(`V9__...sql`)으로 추가한다.

## 문서

| 문서 | 내용 |
|---|---|
| `docs/DESIGN.md` | 전체 설계, 미확정 항목, 상태 모델, 장애 정책, 구현 단계별 완료 기준 |
| `docs/API.md` | 엔드포인트별 요청/응답, `allowedActions` 계약 (SSE 등 일부 **미구현** 항목 표시 포함) |
| `docs/STATE_MACHINES.md` | 작업/로봇 등 상태 전이 다이어그램 (일부 **미구현** 표시 포함) |
| `docs/ERD.md` | 테이블 구조 (일부 **미구현** 표시 포함) |
| `docs/DEPLOYMENT.md` | (35단계) 컨테이너 배포 — 이미지·compose 구성, prod 프로필, 헬스체크·메트릭, 업데이트·백업 |
| `docs/FIELD_VALIDATION.md` | 13단계(현장 검증) 절차서 — 실제 로봇·현장 필요, 이 저장소의 소프트웨어 구현 범위 밖 |
| `AGENTS.md` | 이 저장소에서 작업할 때 지키는 개발 규칙(계층 구조, 변경 범위, 테스트 컨벤션 등) |

## 진행 상태

- **최초 설계 범위** (`docs/DESIGN.md` §11의 0~13단계) 중 **0~12단계를 구현·검증 완료**했다. **13단계(현장 검증)는 실제 로봇·실제 현장이 필요한 운영 활동**이라 이 저장소의 소프트웨어 구현 범위에는 포함하지 않고, 대신 절차서(`docs/FIELD_VALIDATION.md`)로 정리해뒀다.
- 백엔드·화면 구현 이후 사용자 요청으로 추가된 **확장 단계** 현황:

  | 단계 | 내용 |
  |---|---|
  | 14. 알람(Alarm) | `alarms`/`alarm_events` 테이블·API, 로봇 통신 두절·위치추정 실패·적재 이상·작업 최종 실패 자동 발생, 확인/해제 처리 |
  | 15. 실시간 이벤트(Redis) | `GET /api/events/stream`(SSE), Outbox → Redis Pub/Sub 팬아웃, `Last-Event-ID` 재접속 복구 |
  | 16. 실시간 이벤트(Kafka 로그) | 같은 Outbox가 Kafka 토픽 `amr.events`에도 발행하는 내구성 있는 이벤트 로그 |
  | 17. 로봇 기본정보 수정 | `PATCH /api/admin/robots/{robotId}` |
  | 18. 로봇 통신(MQTT/VDA5050) | `RobotGateway`의 두 번째 구현체 `MqttRobotGateway`(옵트인, 기본은 시뮬레이터 유지) |
  | 19. 프론트엔드(알람·실시간 이벤트) | 관리자 화면에 `/alarms`, `/events` 추가 |
  | 20. 프론트엔드 컴포넌트 분리 | 11~19단계 화면 전부를 얇은 페이지 셸 + `src/components/<domain>/` 소형 컴포넌트로 리팩터링(기능 변경 없음) |
  | 21. 로봇 기본정보 수정 화면 | 로봇 상세 화면에 `RobotBasicInfoForm` 추가(17단계 API 사용, 백엔드 변경 없음) |
  | 22. 백엔드 패키지 분리 | `domain/entity`/`dto/request`/`dto/response`/`repository`를 `alarm`/`user`/`site`/`map`/`robot`/`task`/`event` 도메인 하위 패키지로 재배치(기능 변경 없음) |
  | 23. 백엔드 패키지 분리(컨트롤러·서비스) | `controller`/`service`도 같은 7개 도메인 하위 패키지로 재배치(기능 변경 없음) |
  | 24. 지도 장애물·작업장 바인딩 화면 | `obstacles` 테이블·API, 통로-장애물 교차 검증, 작업장↔지도 노드 연결 화면 |
  | 25. 지도 시각 편집기 | SVG 캔버스에서 클릭으로 노드·장애물·통로 배치(`MapCanvasEditor`) |
  | 26. 보안 강화 | 외부 API 키 인증, 작업자 작업장 단위 접근 제어, 비활성 사용자 토큰 즉시 무효화, 최초 관리자 부트스트랩 |
  | 27. 이벤트·복구 신뢰성 | Kafka 발행 결과 확인, SSE 재접속 시 이벤트 공백 제거, 작업 복구 필요 알람(`TASK_RECOVERY_REQUIRED`), MQTT 인터럽트 처리 수정 |
  | 28. 프론트 UX 개선 | 역할별 라우트 가드·404, 전역 실시간 갱신(SSE), 한글 라벨, 확인창·토스트, 작업자 화면 개선, 프론트 CI |
  | 29. 자동 디스패치·연결 감시 | 디스패치 사이클 자동 실행, 하트비트 기반 로봇 연결 감시(끊기면 OFFLINE·알람, MQTT 텔레메트리로 복구) |
  | 30. 대시보드·실시간 지도 | 관리자 첫 화면 대시보드(KPI, 로봇 상태표), 로봇 현재 노드 저장, 활성 지도 위에서 로봇이 통로 최단 경로를 따라 움직이는 모습(시뮬레이터 표시 전용 좌표), 로봇 상태 실시간 이벤트 |
  | 31. 대용량·동시성 | 작업·알람 목록 페이지네이션과 N+1 제거, 동시 배정·작업장 예약 비관적 잠금, 다중 인스턴스 안전 아웃박스(`SKIP LOCKED`), 오래된 이벤트·상태 이력 자동 삭제, 인덱스 |
  | 32. 계정 관리·코드 품질 | 사용자 역할 변경(마지막 관리자 보호), 관리자 비밀번호 초기화·본인 비밀번호 변경(기존 토큰 무효화), 연결 감시 기동 유예, 프론트 lint 경고 0건 |
  | 33. 우선순위·일시정지·비상정지 | 작업 우선순위(호출 시 선택·대기 중 변경), 작업 일시정지·재개, 로봇·물류센터 전체 비상정지(작업 복구 필요·배정 제외·알람), 시뮬레이터 현장 해제 |
  | 34. 운영 통계 | 기간별 처리량·성공률·평균 처리/대기 시간·일별 추이·로봇 가동률·발생 알람(`/statistics` 화면) |
  | 35. 배포 준비 | 백엔드·프론트 Docker 이미지, 전체 스택 운영형 compose, `prod` 프로필(graceful shutdown, 관리 포트), Prometheus 메트릭과 업무 지표 |
  | 36. 로그인 시도 제한 | 아이디별 5회·IP별 50회 실패 시 15분 잠금(429), 성공·관리자 비밀번호 초기화로 해제, 로그인 화면 오류 메시지 개선 |
  | 37. 감사 로그 | 상태를 바꾸는 모든 API 호출(실패 포함)과 로그인 결과를 자동 기록, 관리자 `/audit-logs` 화면(사용자·대상 필터) |
  | 38. 가까운 로봇 선택 | 디스패치가 출발 작업장까지 지도 통로 거리가 가장 가까운 로봇을 배정(위치 모름·길 없음은 뒤로), 디스패치 결과에 로봇·거리 표시 |
  | 39. 로봇 명령 비동기화 | MQTT 로봇 명령이 요청 안에서 결과를 기다리지 않음(커밋 뒤 발행, 결과·타임아웃은 별도 트랜잭션), 이동 중 일시정지 제한 |
  | 40. MQTT 취소·복구 마무리 | 이동 중 취소 시 로봇에 주문 취소 전송, 결과 불명 뒤 도착 보고가 오면 작업 자동 재개, 취소·비상정지 뒤 거짓 통신 두절 보고 방지 |

  각 단계의 완료 기준과 세부 구현 내용은 `docs/DESIGN.md` §11을 참고.

### 진행 중인 로드맵 (코드 리뷰 반영)

25단계 이후 전체 코드 리뷰에서 나온 문제를 아래 순서로 반영하고 있다. 완료된 단계는 위 표로 옮긴다.

| 단계 | 내용 |
|---|---|

### 알려진 제약 · 남은 과제

- MQTT(18단계)는 옵트인이며 기본값은 시뮬레이터다. VDA5050 구현은 축소된 하위 집합이라 실제 로봇 벤더 연동 전 검증이 더 필요하다. 39~40단계에서 명령을 비동기로 바꾸고 이동 중 취소(`cancelOrder`)·늦은 도착 보고 자동 재개를 넣었지만, `cancelOrder`·`stopPause`가 실제로 수행됐는지(`actionStates`)는 확인하지 않는다(`docs/API.md` §7).
- Kafka 토픽(`amr.events`)은 발행만 하고 이 애플리케이션 내부에는 컨슈머가 없다 — 재구독이 필요한 외부 시스템이 생기면 별도로 컨슈머를 구현해야 한다.
- 외부 API 인증(26단계)은 연동 시스템 전체가 공유하는 API 키 하나다. 시스템별 자격증명·키 회전은 실제 WMS/WCS가 정해진 뒤 설계한다.
- 코드 리뷰 뒤 추가로 정한 과제(일시정지·긴급정지 33단계, 통계 34단계, Dockerfile·메트릭 35단계, 로그인 시도 제한 36단계, 감사 로그 37단계, 가까운 로봇 선택 38단계, 로봇 명령 비동기화 39단계)는 모두 구현했다.
- 가까운 로봇 선택(38단계)은 작업마다 차례로 고르는 방식이라 사이클 전체 이동 거리 합이 최소라는 보장이 없고, 거리는 통로 길이만 본다(다른 로봇의 점유·혼잡은 고려하지 않음). 로봇 위치는 시뮬레이터가 이동 완료 시 기록하는 현재 노드라, MQTT 실제 로봇은 위치 보고를 파싱하기 전까지 등록 순서로 배정된다. 자동 충전은 여전히 첫 번째 충전 작업장으로 보낸다.
- 감사 로그(37단계)는 요청 본문을 저장하지 않아 "무엇으로 바꿨는지"(변경 전후 값)는 남지 않고, 보관 기간·아카이빙 정책이 없다. 기록 실패가 원래 요청을 막지 않으므로 드물게 빠질 수 있다.
- 배포(35단계)는 단일 호스트 compose 기준이다. TLS 종단, Kubernetes 매니페스트, 비밀값 관리 도구, Grafana·경보 규칙, 자동 백업은 없다(`docs/DEPLOYMENT.md` §8).
- 운영 통계(34단계)는 요청마다 기간 안의 행을 읽어 계산한다(최대 92일). 물류센터당 작업이 하루 수만 건을 넘으면 일별 집계 테이블(배치 적재)로 옮겨야 한다. 로봇 가동률은 "작업이 배정돼 있던 시간"이라 충전·대기 이동을 따로 구분하지 않는다.
- 비상정지(33단계)의 실제 해제는 현장 조작으로 가정해 원격 해제 API가 없다(시뮬레이터 전용 해제만 있음). MQTT 어댑터는 `stopPause`를 보내기만 하고 로봇의 안전 상태 보고를 파싱하지 않는다.
- 13단계(현장 검증)는 미착수 상태이며, 실제 로봇·현장이 준비되면 `docs/FIELD_VALIDATION.md` 절차를 따라 진행한다.
