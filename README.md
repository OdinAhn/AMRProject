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
| `TOKEN_SIGNING_SECRET` | (필수, 기본값 없음) | 액세스 토큰 서명용 비밀키 |
| `TOKEN_EXPIRY_MINUTES` | `480` | 액세스 토큰 만료 시간(분) |

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

로그인 후 역할(`ADMIN`/`WORKER`)에 따라 관리자 화면(`/sites` 이하)과 작업자 화면(`/worker` 이하)으로 자동 이동한다.

## 데이터베이스 마이그레이션

Flyway 마이그레이션은 `src/main/resources/db/migration`에 있으며, 애플리케이션 기동 시 자동 적용된다. 새 마이그레이션 추가 시 기존 파일은 수정하지 않고 다음 버전(`V9__...sql`)으로 추가한다.

## 문서

| 문서 | 내용 |
|---|---|
| `docs/DESIGN.md` | 전체 설계, 미확정 항목, 상태 모델, 장애 정책, 구현 단계별 완료 기준 |
| `docs/API.md` | 엔드포인트별 요청/응답, `allowedActions` 계약 (SSE 등 일부 **미구현** 항목 표시 포함) |
| `docs/STATE_MACHINES.md` | 작업/로봇 등 상태 전이 다이어그램 (일부 **미구현** 표시 포함) |
| `docs/ERD.md` | 테이블 구조 (일부 **미구현** 표시 포함) |
| `docs/FIELD_VALIDATION.md` | 13단계(현장 검증) 절차서 — 실제 로봇·현장 필요, 이 저장소의 소프트웨어 구현 범위 밖 |
| `AGENTS.md` | 이 저장소에서 작업할 때 지키는 개발 규칙(계층 구조, 변경 범위, 테스트 컨벤션 등) |

## 진행 상태 (최종)

이 프로젝트는 아래 범위까지 구현·검증을 마치고 마무리한 상태다.

- **최초 설계 범위** (`docs/DESIGN.md` §11의 0~13단계) 중 **0~12단계를 구현·검증 완료**했다. **13단계(현장 검증)는 실제 로봇·실제 현장이 필요한 운영 활동**이라 이 저장소의 소프트웨어 구현 범위에는 포함하지 않고, 대신 절차서(`docs/FIELD_VALIDATION.md`)로 정리해뒀다.
- 백엔드·화면 구현 이후 사용자 요청으로 추가된 **확장 단계 14~23을 전부 구현·검증 완료**했다:

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

즉 **현장 검증(13단계)을 제외한 전체 소프트웨어 구현 범위가 완료**된 상태다. 각 단계의 완료 기준과 세부 구현 내용은 `docs/DESIGN.md` §11을 참고.

### 알려진 제약 · 남은 과제

- **최초 관리자 계정 부트스트랩 수단이 없다**: 사용자 생성 API(`POST /api/admin/users`)는 `ADMIN` 권한이 있어야 호출할 수 있어, 완전히 빈 데이터베이스에서는 API만으로 첫 관리자 계정을 만들 수 없다. 지금은 DB에 직접 `users` 행을 삽입(비밀번호는 BCrypt 해시)하는 방식으로 우회한다 — 운영 배포 전에는 별도 시딩 스크립트나 최초 기동 시 관리자 계정 생성 절차가 필요하다.
- MQTT(18단계)는 옵트인이며 기본값은 시뮬레이터다. VDA5050 구현은 축소된 하위 집합이고 동기식·3초 타임아웃 방식이라 실제 로봇 벤더 연동 전 검증이 더 필요하다(`docs/DESIGN.md` §11 18단계 참고).
- Kafka 토픽(`amr.events`)은 발행만 하고 이 애플리케이션 내부에는 컨슈머가 없다 — 재구독이 필요한 외부 시스템이 생기면 별도로 컨슈머를 구현해야 한다.
- 13단계(현장 검증)는 미착수 상태이며, 실제 로봇·현장이 준비되면 `docs/FIELD_VALIDATION.md` 절차를 따라 진행한다.
