# API 스펙 초안 (v0)

`DESIGN.md` §10, `ERD.md`, `STATE_MACHINES.md`를 기준으로 작성한 초안. 엔드포인트·요청/응답 필드는 백엔드 구현(1~10단계) 중 세부 조정될 수 있으나, 리소스 구조와 상태 계약은 이 문서를 기준으로 삼는다.

## 공통 규칙

- **10단계 구현**: `Authorization: Bearer <token>` 방식으로 확정했다. 토큰은 표준 JWT가 아니라 서버가 직접 서명·검증하는 자체 토큰이다(`AccessTokenService`, HMAC-SHA256) — 이 서버 혼자 발급·검증을 다 하고 외부와 상호운용할 필요가 없어 라이브러리 없이 최소 구현했다. `/api/admin/**`은 `ADMIN` role, `/api/worker/**`는 `ADMIN`/`WORKER` role이 필요하다. `/api/external/**`은 외부 시스템 인증 방식이 아직 미정이라(로봇 통신 규격 미정과 연동) 계속 인증 없이 열려 있다.
- 모든 응답은 JPA 엔티티가 아닌 DTO로 반환한다 (AGENTS.md 규칙).
- 오류 응답은 공통 포맷을 사용한다.

```json
{
  "code": "TASK_NOT_FOUND",
  "message": "작업을 찾을 수 없습니다.",
  "details": null
}
```

| 상황 | 상태 코드 |
|---|---|
| 요청 검증 실패 | 400 |
| 인증 필요/실패 | 401 |
| 권한 없음 (다른 작업장 접근 등) | 403 |
| 리소스 없음 | 404 |
| 상태 충돌 (허용되지 않는 전이, 중복 배정 등) | 409 |
| 서버 오류 | 500 |

- **멱등성**: 두 가지 메커니즘을 구분한다. ERD엔 생성 액션의 멱등키(`tasks.idempotency_key`, `inbox_messages.message_key`)만 있고, 상태 전이 액션용 별도 키 저장소는 없다 — 아래처럼 서로 다른 방식으로 안전성을 보장한다.
  - **리소스 생성 API** (`POST /api/worker/tasks`, `POST /api/external/tasks`): `Idempotency-Key` 헤더 필수, DB UNIQUE 제약으로 중복 생성을 막는다. 동일 키로 재요청하면 새로 만들지 않고 기존 리소스를 그대로 반환한다.
  - **상태 전이 API** (`dispatch`, `confirm-unload`, `cancel`, `report-*` 등): 별도 키를 저장하지 않는다. 대신 **요청한 전이가 이미 일어난 상태라면 오류(409) 대신 현재 리소스를 그대로 200으로 반환**해 재시도를 안전하게 만든다. 반대로 현재 상태와 **모순되는** 요청(예: 이미 `CANCELED`인 작업에 `dispatch`)은 409를 반환한다.
- **작업 조회 응답에는 `allowedActions`를 포함**한다. 화면은 이 배열만 보고 버튼을 활성화/비활성화한다. 버튼 실행 API는 응답에 관계없이 서버에서 현재 상태·권한을 다시 검증한다 (재검증 없이 클라이언트 판단만 믿지 않음).

```json
"allowedActions": ["DISPATCH", "CANCEL", "REPORT_OUT_OF_STOCK"]
```

---

## 1. 인증

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/auth/login` | 로그인, 토큰 발급 |
| POST | `/api/auth/logout` | 로그아웃 |
| GET | `/api/auth/me` | 현재 사용자 정보(username, displayName, role). 접근 가능 station 목록은 아직 응답에 포함하지 않는다 — 필요하면 `GET /api/admin/users/{userId}/station-assignments`로 별도 조회 |

토큰이 상태 없는(stateless) 자체 서명 값이라 서버가 들고 있는 세션이 없다 — `logout`은 클라이언트가 토큰을 버리는 것으로 처리되고 서버 측 토큰 무효화(블랙리스트)는 구현하지 않는다.

---

## 2. 관리자 — 시설/지도/로봇/사용자 (환경 설정)

### 2.1 물류센터 (Site)

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/admin/sites` | 목록 (페이지네이션) |
| POST | `/api/admin/sites` | 생성 |
| GET | `/api/admin/sites/{siteId}` | 단건 조회 |
| PATCH | `/api/admin/sites/{siteId}` | 수정 |

### 2.2 작업장 (Station)

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/admin/sites/{siteId}/stations` | 목록 |
| POST | `/api/admin/sites/{siteId}/stations` | 생성 (station_type 지정) |
| GET | `/api/admin/stations/{stationId}` | 단건 조회 |
| PATCH | `/api/admin/stations/{stationId}` | 수정 (이름, status 등) |
| PUT | `/api/admin/stations/{stationId}/map-binding` | 활성 지도 버전의 노드와 매핑 (`station_map_bindings`) |

### 2.3 지도 (Map)

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/admin/sites/{siteId}/maps` | 지도 목록 |
| POST | `/api/admin/sites/{siteId}/maps` | 지도 생성 |
| POST | `/api/admin/maps/{mapId}/versions` | 새 버전 업로드 (파일 + 좌표계) → `DRAFT` 생성 |
| GET | `/api/admin/maps/{mapId}/versions` | 버전 목록 |
| GET | `/api/admin/map-versions/{versionId}` | 버전 상세 (nodes/edges/zones 요약 포함) |
| POST | `/api/admin/map-versions/{versionId}/validate` | 검증 실행 → 성공 시 `VALIDATED`, 실패 시 오류 목록 반환 |
| POST | `/api/admin/map-versions/{versionId}/activate` | 활성화 → 기존 `ACTIVE` 버전은 `ARCHIVED`로 전환 |
| POST | `/api/admin/map-versions/{versionId}/nodes` | 노드 생성 (`DRAFT`에서만 허용) |
| GET | `/api/admin/map-versions/{versionId}/nodes` | 노드 목록 |
| POST | `/api/admin/map-versions/{versionId}/edges` | 통로 생성 (`DRAFT`에서만 허용) |
| GET | `/api/admin/map-versions/{versionId}/edges` | 통로 목록 |
| POST | `/api/admin/map-versions/{versionId}/zones` | 구역 생성 (`DRAFT`에서만 허용, `nodeIds` 포함) |
| GET | `/api/admin/map-versions/{versionId}/zones` | 구역 목록 |

`VALIDATED`가 아닌 버전은 `activate` 호출 시 409. `DRAFT`/`ARCHIVED` 상태에서 노드·통로·구역을 조회할 수는 있지만, 운영 로직(배정/교통)은 오직 `ACTIVE` 버전만 사용한다.

**6단계 구현 결과**: 지도 파일 저장소/형식이 아직 미정이라(DESIGN.md 미확정 항목) 파일 업로드→서버 파싱은 구현하지 않았다. 대신 노드/통로/구역 생성 엔드포인트(POST)를 임시 저작 도구로 추가했다 — `POST .../versions`의 `filePath`/`coordinateSystem`은 참고용 메타데이터일 뿐이고, 실제 토폴로지는 이 엔드포인트들로 직접 입력한다. 지도 파일 형식이 정해지면 파싱 파이프라인으로 교체하고 이 엔드포인트들은 유지하거나 내부 구현으로 재사용할 수 있다.

### 2.4 로봇 (Robot)

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/admin/robots` | 목록 (필터: site, lifecycle_status, connection_state) |
| POST | `/api/admin/robots` | 등록 |
| GET | `/api/admin/robots/{robotId}` | 상세 (기본정보 + 현재 상태 요약) |
| PATCH | `/api/admin/robots/{robotId}` | 기본 정보 수정 — **미구현**. 등록 후 이름/코드 등을 바꿔야 할 실제 운영 요구가 아직 없어 `lifecycle-status` 전환만 구현했다 |
| PATCH | `/api/admin/robots/{robotId}/lifecycle-status` | `IN_SERVICE`/`MAINTENANCE`/`DECOMMISSIONED` 전환 |
| POST | `/api/admin/robots/{robotId}/configurations` | 새 설정 버전 생성 → `desired_config` 갱신, 로봇에 적용 요청 |
| GET | `/api/admin/robots/{robotId}/configurations` | 설정 이력 (요청 버전 vs 적용 버전) |
| GET | `/api/admin/robots/{robotId}/state` | 현재 상태 (다축 상태 전체, `robot_current_states`) |
| GET | `/api/admin/robots/{robotId}/state-history` | 상태 이력 (기간 필터) |

`MAINTENANCE`/`DECOMMISSIONED`인 로봇은 배정 대상에서 자동 제외된다 (§ STATE_MACHINES 4.2 참고).

### 2.5 사용자 (User)

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/admin/users` | 목록 |
| POST | `/api/admin/users` | 생성 |
| GET | `/api/admin/users/{userId}` | 상세 |
| PATCH | `/api/admin/users/{userId}` | 수정 (role, status 등) |
| GET | `/api/admin/users/{userId}/station-assignments` | 작업장 접근 권한 목록 |
| POST | `/api/admin/users/{userId}/station-assignments` | 작업장 접근 권한 부여 |
| DELETE | `/api/admin/users/{userId}/station-assignments/{stationId}` | 권한 회수 |

---

## 3. 관리자 — 관제 (작업/알림)

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/admin/tasks` | 전체 작업 조회 (8단계 구현: `status`/`siteId` 필터만 지원 — station/robot/기간 필터는 아직 미구현) |
| GET | `/api/admin/tasks/{taskId}` | 상세 (단계, 배정 이력, 로봇 명령 포함) |
| GET | `/api/admin/tasks/{taskId}/events` | 상태 전이 이력 (`task_events`) |
| POST | `/api/admin/tasks/{taskId}/assign` | 로봇 지정 배정 (`robotId`) → `ASSIGNED` 후 즉시 첫 단계 시작 |
| POST | `/api/admin/tasks/{taskId}/cancel` | 취소 요청 → 적재 전/하역 후면 즉시 `CANCELED`, 적재 중이면 `RECOVERY_REQUIRED`(8단계, 아래 참고) |
| POST | `/api/admin/tasks/{taskId}/recover` | `RECOVERY_REQUIRED` 상태 개입: 재개 또는 최종 실패 처리 |
| POST | `/api/admin/sites/{siteId}/dispatch` | 디스패치 사이클 실행 (7단계): `QUEUED` 작업 자동 배정 + 저배터리 로봇 자동 충전 작업 생성, 결과로 배정/생성된 taskId 목록 반환. 스케줄러가 없어 관리자가 필요할 때 호출한다 |
| GET | `/api/admin/alarms` | 알람 목록 (필터: status, severity, sourceType) |
| GET | `/api/admin/alarms/{alarmId}` | 상세 |
| POST | `/api/admin/alarms/{alarmId}/acknowledge` | 확인 처리 (`OPEN`→`ACKNOWLEDGED`. 이미 `ACKNOWLEDGED`면 200으로 현재 상태 반환, `RESOLVED`면 409) |
| POST | `/api/admin/alarms/{alarmId}/resolve` | 해제 처리 (`OPEN`/`ACKNOWLEDGED`→`RESOLVED`. 이미 `RESOLVED`면 200으로 현재 상태 반환) |

**14단계 구현**: `alarms`/`alarm_events` 테이블과 위 4개 엔드포인트를 실제로 구현했다. 자동 발생 4종(`ROBOT_COMM_LOST`/`LOCALIZATION_LOST`/`LOAD_EXCEPTION`/`TASK_FAILED`) 중 로봇 관련 3종은 `SimulatorRobotGateway`가 연결/위치추정/적재 센서 상태를 보고할 때 이상→정상 전이를 감지해 자동 발생·자동 해소한다(정상 dispatch 한 번이면 해소). `TASK_FAILED`는 `TaskService.failRecovery()`(위 `recover`의 `FAIL`)에서 발생하며, 종료 상태라 자동 해소가 없어 관리자가 `acknowledge`/`resolve`로 직접 처리해야 한다. 같은 (알람 종류, 대상 종류, 대상 ID) 조합의 열린 알람은 항상 최대 1개다. `alarm_events`는 API로 노출하지 않는 내부 감사 로그다.

`recover` 요청 본문 예시:

```json
{ "resolution": "RESUME" }   // 또는 "FAIL"
```

`RESUME`은 `STATE_MACHINES.md` §1의 `RECOVERY_REQUIRED → RUNNING`, `FAIL`은 `RECOVERY_REQUIRED → FAILED`에 대응한다. 서버는 로봇 실제 상태를 재확인한 뒤에만 `RESUME`을 허용한다.

---

## 4. 작업자 (Worker)

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/worker/stations` | 내 권한이 있는 작업장 목록 (`user_station_assignments` 기준) |
| GET | `/api/worker/stations/{stationId}/tasks` | 해당 작업장의 진행 중/대기 작업 (`SUCCEEDED`/`CANCELED`/`FAILED` 제외) |
| GET | `/api/worker/sites/{siteId}/stations` | (12단계) 로봇 호출 시 목적지 선택용 — 그 site의 station 전체 조회 |
| GET | `/api/worker/tasks/{taskId}` | 작업 상세 + `allowedActions` |
| POST | `/api/worker/tasks` | **로봇 호출** — 운반 요청 생성 (`Idempotency-Key` 필수) |
| POST | `/api/worker/tasks/{taskId}/dispatch` | **로봇 보내기** — 적재 완료 확인 후 다음 이동 요청 |
| POST | `/api/worker/tasks/{taskId}/confirm-unload` | **하역 완료** — 물품 인계 확인, 단계 완료 |
| POST | `/api/worker/tasks/{taskId}/report-out-of-stock` | **재고 없음** — **미구현** (8단계에서 정책 미정으로 확정, `loads`/`load_transfer_events` 없이는 구현 안 함) |
| POST | `/api/worker/tasks/{taskId}/report-unavailable` | **작업 불가** — 사유 기록, `RECOVERY_REQUIRED`로 전이(8단계) |
| POST | `/api/worker/tasks/{taskId}/cancel` | **호출 취소** — `AdminTaskController`와 동일한 `TaskService.requestCancel` 재사용(12단계) |

**12단계 구현**: 위 세 엔드포인트(`GET /stations`, `GET /stations/{id}/tasks`, `POST .../cancel`)가 이번에 실제로 채워졌다. `GET /api/worker/stations/{stationId}/tasks`와 `GET /api/worker/sites/{siteId}/stations`는 station 단위 접근 제어(요청한 사용자가 그 station/site에 실제 권한이 있는지)를 아직 검사하지 않는다 — role 기반 검사(`WORKER`/`ADMIN`)만 적용된다. 세밀한 소유권 검사는 알려진 제한사항으로 남긴다.

### 로봇 호출 요청 예시

```json
POST /api/worker/tasks
Idempotency-Key: 8e2f1c...

{
  "originStationId": 12,
  "destinationStationId": 34,
  "loads": [{ "description": "파렛트 A", "quantity": 1 }]
}
```

응답: `201 Created`, 생성된 `task` (상태 `QUEUED`, `allowedActions: ["CANCEL"]`)

### 버튼 → 상태 전이 매핑 (DESIGN.md §5, STATE_MACHINES.md 기준)

| API | 전제 상태 | 결과 |
|---|---|---|
| `POST /tasks` | - | `QUEUED` 생성 |
| `dispatch` | 현재 단계 `RUNNING`(적재 대기) | 해당 단계 `SUCCEEDED` → 다음 단계 명령 발행 |
| `confirm-unload` | 현재 단계 `RUNNING`(하역 대기) | 해당 단계 `SUCCEEDED` → 마지막 단계면 Task `SUCCEEDED` |
| `report-out-of-stock` | 적재 대기 단계 | **미구현** — `loads`/`load_transfer_events`/WMS 연동 없이는 이후 정책(자동 취소/관리자 대기/재고 갱신)을 임의로 정할 수 없어 8단계에서도 보류 (DESIGN.md §5) |
| `report-unavailable` | `QUEUED`/`ASSIGNED`/`RUNNING`/`PAUSED` | (8단계) `QUEUED`면 즉시 `CANCELED`, 배정된 상태면 `RECOVERY_REQUIRED` + 사유를 `task_events.payload`에 기록 — 별도 알람 테이블 없이 기존 `recover` API로 운영자가 처리 |
| `cancel` | `QUEUED`/`ASSIGNED`/`RUNNING`/`PAUSED` | (8단계) `QUEUED`면 즉시 `CANCELED`. 배정된 상태는 적재 여부로 갈린다: 적재 전이거나 하역까지 끝났으면 즉시 `CANCELED`, 적재 중(로봇에 물건이 있음)이면 `RECOVERY_REQUIRED`로 보내 운영자가 `recover`로 결정 |

`report-out-of-stock` 이후 흐름(자동 취소/관리자 대기/WMS 재고 갱신 여부)은 **미정** — DESIGN.md §5에서 이미 열린 질문으로 표시된 항목.

---

## 5. 실시간 이벤트 (공통)

**15단계 구현**: `GET /api/events/stream`을 Redis Pub/Sub 기반으로 구현했다. `outbox_events`(Task 이벤트는 3단계부터, 알람 이벤트는 14단계부터 기록됨)를 `OutboxPublisher`가 1초 주기로 폴링해 Redis 채널 `amr.events`로 발행하고 `PUBLISHED`로 마킹한다 — 로봇 상태 보고(고빈도)는 스로틀링 설계가 아직 없어 이번 범위에서 제외했다(아래 열린 질문 참고).

**16단계 구현**: 같은 `OutboxPublisher`가 같은 이벤트를 Kafka 토픽 `amr.events`(파티션 1, 복제 1)에도 함께 발행한다 — Redis는 실시간 팬아웃(SSE), Kafka는 나중에 다른 시스템이 재구독/재생할 수 있는 내구성 있는 이벤트 로그 역할이다. 이 앱 내부에는 Kafka 컨슈머가 없다(프로듀서만).

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/events/stream` | SSE 스트림. `Last-Event-ID` 헤더로 재접속 시 그 이후 이벤트를 먼저 재생한 뒤 실시간 구독으로 전환한다. **`siteId`/`stationId` 쿼리 필터는 미구현** — 전체 이벤트를 그대로 전달하므로 클라이언트가 필요하면 걸러 써야 한다 |

### 이벤트 봉투(envelope)

```json
{
  "eventId": 8,
  "eventType": "alarm.raised",
  "aggregateType": "ALARM",
  "aggregateId": 1,
  "stateVersion": null,
  "payload": "작업이 복구 불가로 최종 실패 처리되었습니다: ...",
  "occurredAt": "2026-09-12T11:22:59.736161Z"
}
```

- `eventId`는 설계 초안의 ULID 대신 **`outbox_events.id`(BIGINT AUTO_INCREMENT)**를 그대로 쓴다 — 단일 DB 시스템이라 정렬 가능한 정수 ID로 충분하다. `Last-Event-ID` 헤더도 이 정수값을 그대로 받는다.
- `stateVersion`은 Task/Alarm에는 별도 버전 카운터가 없어 **항상 `null`**이다. 로봇 상태 이벤트가 추가되면(아래 열린 질문) `robot_current_states.state_version`으로 채워질 예정이다.
- `payload`는 `outbox_events.payload_json`을 그대로 문자열로 전달한다 — 대부분의 Task 이벤트는 `null`이고, 취소 사유·알람 메시지처럼 실제 내용이 있는 경우만 채워진다. 전체 최신 상태가 필요하면 클라이언트가 해당 리소스를 다시 조회한다(예: `GET /api/worker/stations/{stationId}/tasks`).
- 재접속 복구 실패 시(너무 오래돼 필요한 백로그가 없는 등) 클라이언트는 전체 재조회로 대체한다.

### 열린 질문 (미구현 남은 부분)

- 로봇 상태 보고(고빈도)를 이 스트림에 포함하는 것 — 위치 업데이트가 완료 이벤트를 밀어내지 않도록 스로틀링(`robot.state.updated`를 초 단위로 묶어 보내는 등) 설계가 필요하다.
- `siteId`/`stationId` 쿼리 필터 — `outbox_events`에 siteId가 없어 Task를 다시 join해야 한다.
- Kafka 토픽의 파티션/복제 전략 — 지금은 단일 브로커 개발 환경 기준 파티션 1·복제 1. 운영 규모에서 재검토 필요.

---

## 6. 외부 시스템 연동 (WMS/WCS 등)

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/external/tasks` | 작업 접수 (`Idempotency-Key` 또는 `externalRef` 필수) |
| GET | `/api/external/tasks/{externalRef}` | 상태/결과 조회 |

**9단계 구현**: 이 두 엔드포인트는 실제 WMS/WCS가 정해지기 전에도 성립하는 최소 접수/조회 계약이라 구현했다 — `POST`는 `Idempotency-Key` 헤더와 바디의 `externalRef` 중 하나만 있으면 되고(작업자 API와 달리 헤더 필수 아님), 재요청 시 둘 중 어느 키로든 같은 작업이면 200 + 기존 리소스를 반환한다(멱등). `GET`은 `externalRef`로 조회해 상태 폴링에 쓴다.

결과 회신 방식 중 **웹훅은 미구현** — DESIGN.md의 "기존 WMS/WCS 유무" 미확정 항목과 연동돼 있어(콜백 URL/인증 방식이 실제 대상에 따라 달라짐), 실제 연동 대상이 정해진 뒤 확정한다. 폴링(`GET`)만으로 "외부 요청부터 결과 회신까지 추적"은 가능하다.

실제 로봇 통신(제조사 규격)·리프트/컨베이어 등 설비 인터록·WMS 재고 연동은 여전히 미구현이다 — 전부 어떤 벤더/시스템을 쓸지가 안 정해진 조달 결정이라 임의로 스펙을 만들지 않는다. 로봇 쪽은 `RobotGateway` 포트(§7 참고)가 이미 이 교체를 위해 존재하므로, 실제 규격이 정해지면 새 어댑터 구현체만 추가하면 된다.

---

## 7. 가상 로봇(시뮬레이터) — 이 문서의 범위 밖

DESIGN.md §1/§4는 "로봇 없는 개발: 실제 통신 계약을 모사하는 가상 로봇"을 4단계(로봇 어댑터·시뮬레이터) 필수 요소로 명시한다. 이 API 문서에는 시뮬레이터용 엔드포인트가 없는데, 의도적인 제외다.

- 시뮬레이터(가상 로봇)는 관리자/작업자가 쓰는 이 REST API가 아니라, **서버가 실제 로봇과 통신할 때 쓸 로봇 통신 채널**(§DESIGN.md 미확정 항목: MQTT/VDA 5050/제조사 API)을 통해 서버에 접속하는 것으로 가정한다. 즉 시뮬레이터는 "가짜 관리자/작업자"가 아니라 "가짜 로봇"이다.
- **4단계 구현 결과**: 로봇 통신 규격이 아직 미정이라, 실제 네트워크/브로커 대신 `RobotGateway` 인터페이스(포트) + `SimulatorRobotGateway`(인프로세스 어댑터)로 구현했다. `TaskService`가 MOVE 타입 단계를 시작할 때 `RobotCommand`를 만들어 이 게이트웨이에 넘기면, 시뮬레이터가 같은 트랜잭션 안에서 즉시 SENT→ACCEPTED→RUNNING→SUCCEEDED(또는 FAILED/UNKNOWN)까지 처리하고 `command_events`/`robot_current_states`/`robot_state_snapshots`를 기록한다. 실제 로봇 통신 규격이 정해지면 같은 `RobotGateway` 인터페이스의 다른 구현체(MQTT 등)로 교체하면 된다 — `TaskService`나 이 문서의 다른 API는 바뀌지 않는다.
- 관리자가 시뮬레이터에 장애를 주입하는 기능은 운영 전용 엔드포인트로 추가했다: `POST /api/admin/robots/{robotId}/simulator/faults` (body: `{"type": "COMMAND_FAILURE" | "COMMUNICATION_LOSS"}`) — 해당 로봇에 대한 다음 명령 디스패치 한 번에만 적용되고 소비된다. 실제 제품 기능이 아니라 테스트/운영 보조 도구이며, 메모리에만 저장돼 재시작 시 초기화된다.
- 로봇 상태 조회는 §2.4에 이미 정의된 `GET /api/admin/robots/{robotId}/state`, `GET /api/admin/robots/{robotId}/state-history`를 그대로 사용한다 (4단계에서 실제 구현).

---

## 8. 열린 질문 (구현 전 확정 필요)

- ~~인증 방식~~ — 10단계에서 자체 서명 토큰으로 확정(§1 참고). 로봇 자체 인증 방식(게이트웨이 mTLS 등)과 외부 시스템(WMS/WCS) 인증은 여전히 미정 — 로봇 통신 규격/WMS 연동 대상 미정과 연동돼 있다.
- `report-out-of-stock` 이후 작업 진행 정책과 WMS 재고 반영 여부.
- ~~실시간 이벤트 팬아웃 인프라~~ — 15단계에서 Redis Pub/Sub으로 확정(§5 참고). Kafka는 팬아웃이 아니라 이벤트 로그 용도로 16단계에서 추가 예정.
- 외부 연동 결과 회신 방식(webhook/polling)과 인증.
- 관리자 "지도 검증(`validate`)"의 구체적 검증 규칙(연결성, 고립 노드, 폭 등) — 지도 데이터 형식이 정해진 뒤 상세화.
- 상태 전이 API의 "이미 완료된 요청은 200으로 현재 상태 반환" 규칙이 모든 전이 액션에 예외 없이 적용 가능한지 — 예: `report-out-of-stock`처럼 부작용이 큰 액션도 재요청 시 아무 일 없다는 듯 200을 줘도 되는지는 액션별로 재검토 필요.
