# API 스펙 초안 (v0)

`DESIGN.md` §10, `ERD.md`, `STATE_MACHINES.md`를 기준으로 작성한 초안. 엔드포인트·요청/응답 필드는 백엔드 구현(1~10단계) 중 세부 조정될 수 있으나, 리소스 구조와 상태 계약은 이 문서를 기준으로 삼는다.

## 공통 규칙

- **10단계 구현**: `Authorization: Bearer <token>` 방식으로 확정했다. 토큰은 표준 JWT가 아니라 서버가 직접 서명·검증하는 자체 토큰이다(`AccessTokenService`, HMAC-SHA256) — 이 서버 혼자 발급·검증을 다 하고 외부와 상호운용할 필요가 없어 라이브러리 없이 최소 구현했다. `/api/admin/**`은 `ADMIN` role, `/api/worker/**`는 `ADMIN`/`WORKER` role이 필요하다.
- **26단계 보안 강화**:
  - 서버는 토큰의 서명·만료만 믿지 않고 **매 요청마다 토큰의 사용자를 DB에서 다시 조회**한다(`TokenAuthenticationFilter`). `DISABLED`로 바뀐 사용자의 토큰은 만료 전이어도 401이 되고, 권한은 토큰에 적힌 role이 아니라 DB의 현재 role로 부여된다.
  - `/api/worker/**`은 role 검사에 더해 **작업장 단위 접근 제어**를 한다(§4, `WorkerAccessService`). 배정받지 않은 작업장·site·작업에 접근하면 403 `STATION_ACCESS_DENIED`를 받는다.
  - `/api/external/**`은 더 이상 인증 없이 열려 있지 않다. 공유 API 키 헤더 `X-API-Key`가 필요하다(§6).
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
| 권한 없음 (role 부족 / 26단계: 배정받지 않은 작업장 접근 — `STATION_ACCESS_DENIED`) | 403 |
| 리소스 없음 | 404 |
| 상태 충돌 (허용되지 않는 전이, 중복 배정 등) | 409 |
| (36단계) 로그인 실패 누적으로 잠김 — `LOGIN_RATE_LIMITED`, `Retry-After` 헤더(초) | 429 |
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
| POST | `/api/auth/login` | 로그인, 토큰 발급. 틀리면 401 `INVALID_CREDENTIALS`, (36단계) 실패가 쌓이면 429 `LOGIN_RATE_LIMITED` |
| POST | `/api/auth/logout` | 로그아웃 |
| PUT | `/api/auth/password` | (32단계) 본인 비밀번호 변경 — `{currentPassword, newPassword(8자 이상)}`. 현재 비밀번호가 틀리면 400 `CURRENT_PASSWORD_MISMATCH`. 성공하면 기존 토큰이 모두 무효가 되므로 새 토큰을 로그인 응답과 같은 모양(`{token, user}`)으로 돌려준다 |
| GET | `/api/auth/me` | 현재 사용자 정보(username, displayName, role). 접근 가능 station 목록은 아직 응답에 포함하지 않는다 — 필요하면 `GET /api/admin/users/{userId}/station-assignments`로 별도 조회 |

토큰이 상태 없는(stateless) 자체 서명 값이라 서버가 들고 있는 세션이 없다 — `logout`은 클라이언트가 토큰을 버리는 것으로 처리되고 서버 측 토큰 무효화(블랙리스트)는 구현하지 않는다. 대신 26단계부터 매 요청마다 사용자 상태를 확인하므로, 계정을 `DISABLED`로 바꾸면 그 사용자의 모든 토큰이 즉시 무효가 된다.

**32단계 — 비밀번호 변경과 토큰 무효화**: 토큰에 발급 시각(`iat`, 초)을 넣고, `users.password_changed_at`보다 먼저 발급된 토큰은 인증하지 않는다(본인 변경·관리자 초기화 모두). 서버에 토큰을 저장하지 않는 방식을 유지하면서도, 비밀번호를 바꾸면 도난당했거나 다른 기기에 남은 토큰을 끊을 수 있다. 32단계 이전에 발급돼 `iat`이 없는 토큰은 발급 시각 0으로 보아, 비밀번호를 바꾼 적이 있는 사용자면 무효가 된다. 초 단위 비교라 변경과 같은 초에 발급된 토큰은 유효하게 남는다.

**36단계 — 로그인 시도 제한** (`LoginAttemptLimiter`, 카운터는 Redis):
- **아이디별**: 첫 실패부터 15분 안에 5번 틀리면 그 아이디를 15분 동안 막는다. 막힌 동안은 비밀번호가 맞아도 429다(비밀번호를 확인하지 않으므로 그 요청은 실패로 세지 않는다). 아이디는 대소문자를 구분하지 않고 센다(MySQL 기본 콜레이션상 같은 계정). **없는 아이디도 똑같이 세고 막는다** — 잠김 여부로 아이디 존재가 드러나지 않는다.
- **IP별**: 한 접속 주소에서 15분 안에 50번 틀리면 그 주소를 15분 동안 막는다(여러 아이디를 번갈아 시도하는 경우). 같은 사무실 NAT 뒤 작업자들이 섞일 수 있어 넉넉히 뒀다.
- 응답: 429 `{code: "LOGIN_RATE_LIMITED", message: "로그인 실패가 너무 많습니다. N분 후에 다시 시도하세요."}`, `Retry-After`는 남은 잠금 시간(초, 아이디·IP 중 긴 쪽).
- 로그인에 성공하거나 관리자가 비밀번호를 초기화(`PUT /api/admin/users/{userId}/password`)하면 그 아이디의 카운터를 지운다. 누군가 일부러 틀려 다른 사람을 잠근 경우 관리자가 초기화로 바로 풀 수 있다. IP 카운터는 시간이 지나야 풀린다.
- 기준·시간은 설정(`app.auth.login-limit.*`)으로 바꾼다. **Redis 장애 시에는 제한 없이 로그인을 허용한다**(fail-open, 장애 대응 중 운영자 로그인을 막지 않기 위해).
- 클라이언트 IP는 `request.getRemoteAddr()`다. prod 프로필은 리버스 프록시의 `X-Forwarded-For`를 반영하며, 컨테이너 nginx는 이 헤더를 실제 접속 주소로 **덮어써** 클라이언트가 조작한 값을 믿지 않는다(docs/DEPLOYMENT.md §3).

**최초 관리자 계정(26단계)**: 사용자 생성 API는 `ADMIN`이 필요해 빈 DB에서는 API만으로 첫 관리자를 만들 수 없다. 환경 변수 `BOOTSTRAP_ADMIN_USERNAME`/`BOOTSTRAP_ADMIN_PASSWORD`가 둘 다 설정돼 있고 `ADMIN` 사용자가 한 명도 없으면, 기동 시 `AdminBootstrapRunner`가 관리자 계정을 한 번 만든다. 관리자가 이미 있으면 아무것도 하지 않는다.

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
| POST | `/api/admin/map-versions/{versionId}/obstacles` | (24단계) 장애물(통행 불가 사각 영역: `x`/`y`/`width`/`height`) 생성 (`DRAFT`에서만 허용) |
| GET | `/api/admin/map-versions/{versionId}/obstacles` | (24단계) 장애물 목록 |

**24단계**: `validate`가 통로(두 노드를 잇는 선분)와 장애물 사각형의 교차를 검사한다(Liang-Barsky). 겹치는 통로가 있으면 검증에 실패한다. 따라서 `ACTIVE` 지도의 모든 통로는 장애물을 지나지 않는다. **25단계**: 관리자 화면의 지도 버전 상세가 폼 입력 대신 SVG 캔버스 편집기(`MapCanvasEditor`)로 바뀌었다. API는 그대로다.

`VALIDATED`가 아닌 버전은 `activate` 호출 시 409. `DRAFT`/`ARCHIVED` 상태에서 노드·통로·구역을 조회할 수는 있지만, 운영 로직(배정/교통)은 오직 `ACTIVE` 버전만 사용한다.

**6단계 구현 결과**: 지도 파일 저장소/형식이 아직 미정이라(DESIGN.md 미확정 항목) 파일 업로드→서버 파싱은 구현하지 않았다. 대신 노드/통로/구역 생성 엔드포인트(POST)를 임시 저작 도구로 추가했다 — `POST .../versions`의 `filePath`/`coordinateSystem`은 참고용 메타데이터일 뿐이고, 실제 토폴로지는 이 엔드포인트들로 직접 입력한다. 지도 파일 형식이 정해지면 파싱 파이프라인으로 교체하고 이 엔드포인트들은 유지하거나 내부 구현으로 재사용할 수 있다.

### 2.4 로봇 (Robot)

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/admin/robots` | 목록 (필터: site, lifecycle_status, connection_state). (30단계) 응답에 `connectionState`/`batteryPct`(상태 보고가 없으면 null)가 포함된다 — 필터는 여전히 `siteId`만 구현 |
| POST | `/api/admin/robots` | 등록 |
| GET | `/api/admin/robots/{robotId}` | 상세 (기본정보 + 현재 상태 요약) |
| PATCH | `/api/admin/robots/{robotId}` | 기본 정보 수정 (17단계 구현) — `name`/`model`/`manufacturer`만 변경 가능. `code`/`site`는 등록 후 변경하지 않는다 |
| PATCH | `/api/admin/robots/{robotId}/lifecycle-status` | `IN_SERVICE`/`MAINTENANCE`/`DECOMMISSIONED` 전환 |
| POST | `/api/admin/robots/{robotId}/configurations` | 새 설정 버전 생성 → `desired_config` 갱신, 로봇에 적용 요청 |
| GET | `/api/admin/robots/{robotId}/configurations` | 설정 이력 (요청 버전 vs 적용 버전) |
| GET | `/api/admin/robots/{robotId}/state` | 현재 상태 (다축 상태 전체, `robot_current_states`) |
| GET | `/api/admin/robots/{robotId}/state-history` | 상태 이력 (기간 필터 — **미구현**). (31단계) 최신순 최근 `limit`건(기본 100, 1~1000으로 맞춤) |

**30단계**: `state`/`state-history` 응답에 `currentNodeId`(로봇이 마지막으로 도착한 지도 노드, 모르면 null)를 추가했다.

`MAINTENANCE`/`DECOMMISSIONED`인 로봇은 배정 대상에서 자동 제외된다 (§ STATE_MACHINES 4.2 참고).

### 2.5 사용자 (User)

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/admin/users` | 목록 |
| POST | `/api/admin/users` | 생성 |
| GET | `/api/admin/users/{userId}` | 상세 |
| PATCH | `/api/admin/users/{userId}` | 수정 — `displayName`/`status`, (32단계) `roleCode`. 활성 관리자가 한 명도 남지 않게 되는 강등·비활성화는 409 `LAST_ACTIVE_ADMIN` |
| PUT | `/api/admin/users/{userId}/password` | (32단계) 관리자 비밀번호 초기화 — `{newPassword(8자 이상)}`, 204. 그 사용자의 기존 토큰은 모두 무효가 된다 |
| GET | `/api/admin/users/{userId}/station-assignments` | 작업장 접근 권한 목록 |
| POST | `/api/admin/users/{userId}/station-assignments` | 작업장 접근 권한 부여 |
| DELETE | `/api/admin/users/{userId}/station-assignments/{stationId}` | 권한 회수 |

---

## 3. 관리자 — 관제 (작업/알림)

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/admin/tasks` | 전체 작업 조회 (8단계 구현: `status`/`siteId` 필터만 지원 — station/robot/기간 필터는 아직 미구현). **31단계: 페이지 응답**(아래 참고) |
| GET | `/api/admin/tasks/{taskId}` | 상세 (단계, 배정 이력, 로봇 명령 포함) |
| GET | `/api/admin/tasks/{taskId}/events` | 상태 전이 이력 (`task_events`) |
| POST | `/api/admin/tasks/{taskId}/assign` | 로봇 지정 배정 (`robotId`) → `ASSIGNED` 후 즉시 첫 단계 시작 |
| POST | `/api/admin/tasks/{taskId}/cancel` | 취소 요청 → 적재 전/하역 후면 즉시 `CANCELED`, 적재 중이면 `RECOVERY_REQUIRED`(8단계, 아래 참고) |
| POST | `/api/admin/tasks/{taskId}/recover` | `RECOVERY_REQUIRED` 상태 개입: 재개 또는 최종 실패 처리 |
| POST | `/api/admin/tasks/{taskId}/pause` | (33단계) 작업 흐름 일시정지 `RUNNING → PAUSED`. 이미 PAUSED면 200 그대로. 로봇을 물리적으로 세우지는 않는다 |
| POST | `/api/admin/tasks/{taskId}/resume` | (33단계) 일시정지 해제 `PAUSED → RUNNING`. 이미 RUNNING이면 200 그대로 |
| PATCH | `/api/admin/tasks/{taskId}/priority` | (33단계) 우선순위 변경 `{priority: 0~9}` — `QUEUED`에서만, 배정 뒤에는 409 `TASK_CHANGE_PRIORITY_NOT_ALLOWED` |
| POST | `/api/admin/robots/{robotId}/emergency-stop` | (33단계) 로봇 비상정지 — 아래 참고 |
| POST | `/api/admin/robots/{robotId}/simulator/emergency-stop-release` | (33단계, 시뮬레이터 전용) 현장 비상정지 해제를 흉내 낸다, 204 |
| POST | `/api/admin/sites/{siteId}/emergency-stop` | (33단계) 물류센터의 운영 중(`IN_SERVICE`) 로봇 전체 비상정지 — 로봇별 결과 배열 |
| POST | `/api/admin/sites/{siteId}/dispatch` | 디스패치 사이클 실행 (7단계): `QUEUED` 작업 자동 배정 + 저배터리 로봇 자동 충전 작업 생성, 결과로 배정/생성된 taskId 목록 반환. (29단계) 같은 사이클이 `AutoDispatchScheduler`로 운영 중(`ACTIVE`)인 모든 물류센터에서 주기적으로(기본 5초) 자동 실행된다. 이 API는 즉시 한 번 더 돌리고 결과를 보고 싶을 때 쓴다 |
| GET | `/api/admin/sites/{siteId}/overview` | (30단계) 관제 대시보드 현황 — 아래 참고 |
| GET | `/api/admin/sites/{siteId}/statistics` | (34단계) 운영 통계 — 쿼리 `from`, `to`(YYYY-MM-DD, 양 끝 포함), `zone`(기본 `Asia/Seoul`). 아래 참고 |
| GET | `/api/admin/alarms` | 알람 목록 (필터: status, severity, sourceType). **31단계: 페이지 응답**(아래 참고) |
| GET | `/api/admin/alarms/{alarmId}` | 상세 |
| POST | `/api/admin/alarms/{alarmId}/acknowledge` | 확인 처리 (`OPEN`→`ACKNOWLEDGED`. 이미 `ACKNOWLEDGED`면 200으로 현재 상태 반환, `RESOLVED`면 409) |
| POST | `/api/admin/alarms/{alarmId}/resolve` | 해제 처리 (`OPEN`/`ACKNOWLEDGED`→`RESOLVED`. 이미 `RESOLVED`면 200으로 현재 상태 반환) |
| GET | `/api/admin/audit-logs` | (37단계) 감사 로그 — 필터 `actorUsername`, `targetType`, `targetId`, `page`, `size`. 최신순 페이지 응답. 아래 참고 |

**37단계 — 감사 로그** `GET /api/admin/audit-logs`: 누가 언제 무엇을 바꿨는지 추적한다(`ADMIN`만 조회).

```json
{ "id": 812, "createdAt": "2026-09-14T01:46:48Z", "actorUserId": 1, "actorUsername": "admin",
  "action": "POST /api/admin/robots/{robotId}/emergency-stop", "targetType": "ROBOT", "targetId": 1,
  "httpStatus": 200, "payload": "{\"pathVariables\":{\"robotId\":\"1\"}}", "ipAddress": "10.0.0.5" }
```

- **무엇이 남나**: 컨트롤러까지 도달한 **상태를 바꾸는 모든 요청**(POST/PUT/PATCH/DELETE, `/api/admin`·`/api/worker`·`/api/external`·`/api/auth`)이 자동으로 남는다(`AuditLogInterceptor`). 엔드포인트마다 표시하지 않으므로 새 API도 빠지지 않는다. 409·400 같은 실패도 응답 상태와 함께 남는다. 인증(401)·역할(403)에서 보안 필터가 먼저 거절한 요청과 조회(GET)는 남지 않는다.
- **로그인**: 요청만으로 행위자를 알 수 없어 `AuthService`가 직접 남긴다. `AUTH_LOGIN_SUCCEEDED`, `AUTH_LOGIN_FAILED`(payload `reason`: `UNKNOWN_USERNAME`/`USER_NOT_ACTIVE`/`WRONG_PASSWORD`), `AUTH_LOGIN_RATE_LIMITED`(36단계). 응답은 실패 사유를 숨기지만 감사 로그에는 적는다.
- `action`: 메서드 + **경로 패턴**(값이 아닌 템플릿)이라 같은 동작끼리 모아 볼 수 있다. 화면은 한글 이름으로 바꿔 보여주고, 모르는 값은 그대로 보여준다.
- `targetType`/`targetId`: 첫 번째 경로 변수(`taskId`→`TASK`, `robotId`→`ROBOT`, `versionId`→`MAP_VERSION` 등). 하위 리소스 생성(`POST /api/admin/sites/{siteId}/stations`)은 상위 리소스가 대상이다. 경로에 대상이 없는 생성 API는 대상이 비고, 201이면 만든 리소스 id를 `payload.createdId`로 남긴다.
- `payload`: 경로 변수·쿼리 파라미터·`createdId`(로그인은 사유). **요청 본문은 저장하지 않는다** — 비밀번호가 들어갈 수 있기 때문이다. 그래서 "무엇으로 바꿨는지"(예: 새 우선순위 값)는 감사 로그에 없고, 결과는 해당 리소스와 기존 이력(`task_events`, `robot_configurations` 등)으로 확인한다.
- `actorUserId`/`actorUsername`: 외부 시스템(API 키)은 `external-system`, 없는 아이디로 로그인 실패하면 id 없이 입력한 아이디만 남는다.
- 기록은 원래 작업과 **별도 트랜잭션**이고, 기록에 실패해도 원래 요청은 실패시키지 않는다(오류 로그만). 감사 로그 저장소 문제로 비상정지 같은 운영 동작이 막히면 안 된다고 봤다. 대신 드물게 기록이 빠질 수 있다.
- 보관: 지우지 않는다(`task_events`와 같은 감사 추적 성격). 보관 기간·아카이빙은 남은 과제다.

**14단계 구현**: `alarms`/`alarm_events` 테이블과 위 4개 엔드포인트를 실제로 구현했다. 자동 발생 4종(`ROBOT_COMM_LOST`/`LOCALIZATION_LOST`/`LOAD_EXCEPTION`/`TASK_FAILED`) 중 로봇 관련 3종은 `SimulatorRobotGateway`가 연결/위치추정/적재 센서 상태를 보고할 때 이상→정상 전이를 감지해 자동 발생·자동 해소한다(정상 dispatch 한 번이면 해소). `TASK_FAILED`는 `TaskService.failRecovery()`(위 `recover`의 `FAIL`)에서 발생하며, 종료 상태라 자동 해소가 없어 관리자가 `acknowledge`/`resolve`로 직접 처리해야 한다. 같은 (알람 종류, 대상 종류, 대상 ID) 조합의 열린 알람은 항상 최대 1개다. `alarm_events`는 API로 노출하지 않는 내부 감사 로그다.

**27단계**: 알람 종류에 `TASK_RECOVERY_REQUIRED`를 추가했다. 작업이 `RECOVERY_REQUIRED`가 되는 모든 경로(명령 실패·불명, 적재 중 취소, 작업 불가 보고)에서 발생하고, `recover` 시 자동 해소된다. 발생·해소 규칙 전체 표는 `STATE_MACHINES.md` §7에 있다.

**19단계**: 응답에 `allowedActions`를 추가했다(Task/Robot 응답과 같은 패턴) — `OPEN`→`["ACKNOWLEDGE","RESOLVE"]`, `ACKNOWLEDGED`→`["RESOLVE"]`, `RESOLVED`→`[]`. 관리자 화면(`/alarms`)이 이 배열만 보고 버튼을 그린다.

**33단계 — 우선순위·일시정지·비상정지**:
- **우선순위**: 작업 생성(`POST /api/worker/tasks`, `POST /api/external/tasks`)에 선택 필드 `priority`(0~9, 기본 0)를 받는다. 자동·수동 디스패치는 우선순위 내림차순, 같으면 먼저 들어온 순서로 배정한다(7단계부터 쿼리는 이미 그 순서였지만 값을 바꿀 방법이 없었다). 화면은 보통(0)·높음(5)·긴급(9)을 고른다.
- **일시정지**: 작업 흐름만 멈춘다. `PAUSED` 작업은 작업자 단계 버튼(`DISPATCH`/`CONFIRM_UNLOAD`)이 사라지고, API로 호출해도 409다. 로봇 물리 정지와는 별개다(STATE_MACHINES.md §1).
- **비상정지**: `EMERGENCY_STOP` 명령을 로봇에 보내고, 그 로봇이 맡은 `ASSIGNED`/`RUNNING`/`PAUSED`/`CANCEL_REQUESTED` 작업을 `RECOVERY_REQUIRED`로 보낸다(`TASK_RECOVERY_REQUIRED` 알람, 원인 "로봇 비상정지"). 명령 전달이 불확실(UNKNOWN)해도 작업은 멈춘 것으로 다룬다.
  - 응답 `{robotId, robotCode, commandNo, commandStatus}`의 `commandStatus`는 **전달 여부일 뿐**이다. 실제로 멈췄는지는 `GET .../state`의 `safetyState`로 확인한다(DESIGN.md §3). 시뮬레이터는 즉시 `EMERGENCY_STOP`을 보고하고 명령을 SUCCEEDED로 끝낸다. MQTT 어댑터는 VDA 5050 `instantActions`의 `stopPause`를 발행하고 ACCEPTED까지만 둔다(축소 구현이 안전 상태를 파싱하지 않음).
  - 로봇이 `EMERGENCY_STOP`을 보고하면 `ROBOT_EMERGENCY_STOP`(CRITICAL) 알람이 뜨고, 자동 배정 대상에서 빠지며(`safetyState = NORMAL` 조건 추가), 시뮬레이터는 새 이동 명령을 거부한다(FAILED → 작업 복구 필요).
  - 해제는 현장에서만 가능하다고 가정한다(STATE_MACHINES.md §4.4). 시뮬레이터 전용 해제 API가 로봇의 NORMAL 보고를 흉내 내고, 알람은 자동 해소된다.
- `allowedActions`에 관리자용 `PAUSE`(RUNNING)·`RESUME`(PAUSED)·`CHANGE_PRIORITY`(QUEUED)를 **기존 값 뒤에** 붙였다.

**31단계 — 목록 페이지네이션 (응답 모양이 바뀐 호환성 깨짐 변경)**: `GET /api/admin/tasks`와 `GET /api/admin/alarms`는 배열 대신 페이지 객체를 반환한다. 예전엔 조건에 맞는 행을 전부 읽어 반환했다(알람은 전체를 읽어 메모리에서 필터링). 같은 PR에서 관리자 화면(작업·알람 목록, 사이드바 알람 수)을 함께 바꿨다.

| 요청 파라미터 | 기본값 | 설명 |
|---|---|---|
| `page` | `0` | 0부터 시작. 음수는 0으로 본다 |
| `size` | `20` | 1~100으로 맞춘다(범위 밖이면 400 대신 가장 가까운 값) |

정렬은 최신순(`createdAt` 내림차순, 같으면 `id` 내림차순) 고정이다.

```json
{ "content": [ /* TaskResponse 또는 AlarmResponse */ ], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3 }
```

작업 목록은 한 페이지의 작업·물류센터·작업장·배정 로봇을 한 쿼리로, 단계는 페이지의 작업 id로 한 번에 읽는다(작업마다 추가 조회가 나가던 N+1 제거). 작업자 작업장별 작업 조회(`GET /api/worker/stations/{stationId}/tasks`)도 종료 상태 제외를 DB 쿼리로 내리고 단계를 한 번에 읽는다. 응답 모양은 그대로 배열이다.

**30단계 — 대시보드 개요** `GET /api/admin/sites/{siteId}/overview`: 화면이 로봇·작업·알람 API를 따로 여러 번 부르지 않도록 한 번에 모은다.

```json
{
  "siteId": 1,
  "activeMapVersionId": 3,
  "robots": [
    { "robotId": 1, "code": "AMR-01", "name": "로봇1", "lifecycleStatus": "IN_SERVICE",
      "connectionState": "ONLINE", "motionState": "IDLE", "batteryPct": 92,
      "currentNodeId": 12, "currentTaskId": 40, "currentTaskNo": "0fd5ff69-..." }
  ],
  "openTaskCounts": { "QUEUED": 2, "ASSIGNED": 0, "RUNNING": 1, "PAUSED": 0, "CANCEL_REQUESTED": 0, "RECOVERY_REQUIRED": 0 },
  "openAlarmCount": 1
}
```

- `activeMapVersionId`: 물류센터의 활성 지도 버전. 지도가 여러 개면 id가 가장 작은 지도의 활성 버전이고, 없으면 null이다.
- `robots[].connectionState`/`motionState`/`batteryPct`/`currentNodeId`: 상태 보고가 없는 로봇은 null이다. `currentTaskId`/`currentTaskNo`는 ACTIVE 배정이 없으면 null이다.
- `openTaskCounts`: 종료되지 않은 6개 상태를 모두 키로 넣고, 0건도 0으로 준다.
- `openAlarmCount`: 이 물류센터 소속 로봇·작업이 대상인 `OPEN`/`ACKNOWLEDGED` 알람 수다(`alarms`에 site 컬럼이 없어 대상 테이블로 거슬러 올라가 센다).

**34단계 — 운영 통계** `GET /api/admin/sites/{siteId}/statistics?from=2026-09-07&to=2026-09-13&zone=Asia/Seoul`:

```json
{
  "siteId": 1, "from": "2026-09-07", "to": "2026-09-13", "zone": "Asia/Seoul",
  "tasks": { "created": 120, "succeeded": 110, "failed": 2, "canceled": 5,
             "successRatePct": 98.2, "avgLeadTimeSeconds": 412, "avgWaitSeconds": 35 },
  "daily": [ { "date": "2026-09-07", "created": 18, "succeeded": 17, "failed": 0, "canceled": 1 } ],
  "robots": [ { "robotId": 1, "code": "AMR-01", "name": "로봇1", "completedTasks": 60,
                "busySeconds": 250000, "utilizationPct": 41.3 } ],
  "alarms": [ { "alarmType": "ROBOT_COMM_LOST", "severity": "CRITICAL", "count": 3 } ]
}
```

- **기간**: `zone` 기준 날짜 `[from 00:00, to+1 00:00)`이다. 둘 다 생략하면 오늘까지 최근 7일이고, `to`만 주면 그날까지 7일이다. 시작일이 종료일보다 늦거나 92일을 넘으면 400 `STATISTICS_RANGE_INVALID`, 모르는 시간대면 400 `STATISTICS_ZONE_INVALID`, 날짜 형식이 틀리면 400 `VALIDATION_FAILED`다. DB는 UTC로 저장하므로 날짜 경계와 `daily` 구분은 모두 `zone`으로 계산한다.
- `tasks.created`는 기간 안에 **생성**된 작업 수, `succeeded`/`failed`/`canceled`는 기간 안에 **종료**(`completed_at`)된 작업 수다. 같은 작업이 두 기준에서 다른 날에 잡힐 수 있다.
- `successRatePct` = 성공 ÷ (성공 + 실패) × 100(소수 첫째 자리). 취소는 운영 판단이라 뺀다. `avgLeadTimeSeconds`는 성공 작업의 생성→완료 평균, `avgWaitSeconds`는 종료된 작업 중 로봇이 배정된 적 있는 작업의 생성→첫 배정 평균이다. 대상이 없으면 셋 다 null이다.
- `daily`는 `from`~`to`의 모든 날짜를 0건도 빠짐없이 채운다.
- `robots`: 물류센터 소속 로봇 전체(코드순). `busySeconds`는 기간과 겹치는 배정 구간(`task_assignments.assigned_at`~`released_at`, 진행 중이면 지금까지)의 합이고, `utilizationPct`는 기간 중 **이미 지난 시간** 대비 비율이다(오늘이 포함된 기간이 하루 끝까지 기다리지 않도록). 기간이 아직 시작되지 않았으면 null. `completedTasks`는 기간 안에 완료(`COMPLETED`)된 배정 수다. 충전 작업도 배정이라 가동 시간에 들어간다.
- `alarms`: 기간 안에 **발생**한 알람 수를 종류·심각도별로 많은 순으로 준다(해소 여부 무관, 이 물류센터 로봇·작업 대상).
- 실시간 이벤트로 다시 계산하지 않는다. 화면은 새로고침 버튼으로 다시 조회한다.

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

**12단계 구현**: 위 세 엔드포인트(`GET /stations`, `GET /stations/{id}/tasks`, `POST .../cancel`)가 이번에 실제로 채워졌다. `GET /api/worker/stations/{stationId}/tasks`와 `GET /api/worker/sites/{siteId}/stations`는 station 단위 접근 제어(요청한 사용자가 그 station/site에 실제 권한이 있는지)를 아직 검사하지 않는다 — role 기반 검사(`WORKER`/`ADMIN`)만 적용된다. ~~세밀한 소유권 검사는 알려진 제한사항으로 남긴다.~~ → 26단계에서 해소(아래).

**26단계 — 작업장 단위 접근 제어**: 권한 기준은 `user_station_assignments`이고 `ADMIN`은 검사하지 않는다. 위반 시 403 `STATION_ACCESS_DENIED`.

| 엔드포인트 | 필요한 권한 |
|---|---|
| `GET /stations` | (제한 없음 — 원래 내 배정 작업장만 반환) |
| `GET /stations/{stationId}/tasks` | 그 작업장에 배정돼 있어야 함 |
| `GET /sites/{siteId}/stations` | 그 site 안에 배정된 작업장이 하나 이상 있어야 함 |
| `POST /tasks` (로봇 호출) | **출발지** 작업장에 배정돼 있어야 함 (목적지는 같은 site 어디든 선택 가능) |
| `GET /tasks/{taskId}`, `dispatch`, `confirm-unload`, `report-unavailable`, `cancel` | 작업의 **출발지 또는 도착지** 작업장 중 하나에 배정돼 있어야 함 — 적재(출발지)와 하역(도착지) 작업자가 서로 다른 버튼을 누르기 때문 |

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

응답: `201 Created`, 생성된 `task` (상태 `QUEUED`, `allowedActions: ["CANCEL", "REPORT_UNAVAILABLE"]`)

### 작업 응답(`TaskResponse`) — 28단계 추가 필드

화면이 id만으로는 작업을 알아보기 어려워 표시용 필드를 추가했다. 기존 필드는 그대로라 하위 호환이다.

| 필드 | 설명 |
|---|---|
| `originStationName` / `destinationStationName` | 출발지·도착지 작업장 이름 |
| `assignedRobotId` / `assignedRobotCode` | 현재 배정된 로봇(없으면 `null`) |

`allowedActions` 값 전체(28단계 기준):

| 값 | 조건 | 화면 버튼 |
|---|---|---|
| `CANCEL` | 상태가 `QUEUED`/`ASSIGNED`/`RUNNING`/`PAUSED` | 호출 취소 / 취소 |
| `DISPATCH` | 작업이 `RUNNING`이고 진행 중 단계가 `CONFIRM_LOAD` | 로봇 보내기 |
| `CONFIRM_UNLOAD` | 작업이 `RUNNING`이고 진행 중 단계가 `CONFIRM_UNLOAD` | 하역 완료 |
| `REPORT_UNAVAILABLE` | (28단계) 상태가 `QUEUED`/`ASSIGNED`/`RUNNING`/`PAUSED` — `report-unavailable`이 받는 상태와 같음 | 작업 불가 |
| `PAUSE` / `RESUME` | (33단계) `RUNNING` / `PAUSED` — 관리자 화면 | 일시정지 / 일시정지 해제 |
| `CHANGE_PRIORITY` | (33단계) `QUEUED` — 관리자 화면 | 우선순위 선택 |

배열 순서는 위 표 순서를 따른다(예: 적재 대기 중이면 `["CANCEL", "DISPATCH", "REPORT_UNAVAILABLE"]`).

33단계: 화면 버튼과 별개로 서버도 `dispatch`/`confirm-unload`를 작업이 `RUNNING`일 때만 받는다(409). 28단계에서 버튼만 숨겨, 일시정지·복구 필요 작업에 API로 `dispatch`를 부르면 로봇에 이동 명령이 나가던 문제를 막았다.

28단계 이전에는 `DISPATCH`/`CONFIRM_UNLOAD`를 단계만 보고 계산했다. 그래서 `RECOVERY_REQUIRED`처럼 진행 중이 아닌 작업에도 버튼이 나갔고, 누르면 409가 났다. 지금은 작업이 `RUNNING`일 때만 준다. 복구 필요 작업은 관리자가 `recover`의 `RESUME`을 한 뒤에 다시 버튼이 나온다.

### 버튼 → 상태 전이 매핑 (DESIGN.md §5, STATE_MACHINES.md 기준)

| API | 전제 상태 | 결과 |
|---|---|---|
| `POST /tasks` | - | `QUEUED` 생성 |
| `dispatch` | 현재 단계 `RUNNING`(적재 대기) | 해당 단계 `SUCCEEDED` → 다음 단계 명령 발행 |
| `confirm-unload` | 현재 단계 `RUNNING`(하역 대기) | 해당 단계 `SUCCEEDED` → 마지막 단계면 Task `SUCCEEDED` |
| `report-out-of-stock` | 적재 대기 단계 | **미구현** — `loads`/`load_transfer_events`/WMS 연동 없이는 이후 정책(자동 취소/관리자 대기/재고 갱신)을 임의로 정할 수 없어 8단계에서도 보류 (DESIGN.md §5) |
| `report-unavailable` | `QUEUED`/`ASSIGNED`/`RUNNING`/`PAUSED` | (8단계) `QUEUED`면 즉시 `CANCELED`, 배정된 상태면 `RECOVERY_REQUIRED` + 사유를 `task_events.payload`에 기록 — 기존 `recover` API로 운영자가 처리. (27단계) 이때 사유가 담긴 `TASK_RECOVERY_REQUIRED` 알람도 함께 발생한다 |
| `cancel` | `QUEUED`/`ASSIGNED`/`RUNNING`/`PAUSED` | (8단계) `QUEUED`면 즉시 `CANCELED`. 배정된 상태는 적재 여부로 갈린다: 적재 전이거나 하역까지 끝났으면 즉시 `CANCELED`, 적재 중(로봇에 물건이 있음)이면 `RECOVERY_REQUIRED`로 보내 운영자가 `recover`로 결정 |

`report-out-of-stock` 이후 흐름(자동 취소/관리자 대기/WMS 재고 갱신 여부)은 **미정** — DESIGN.md §5에서 이미 열린 질문으로 표시된 항목.

---

## 5. 실시간 이벤트 (공통)

**15단계 구현**: `GET /api/events/stream`을 Redis Pub/Sub 기반으로 구현했다. `outbox_events`(Task 이벤트는 3단계부터, 알람 이벤트는 14단계부터 기록됨)를 `OutboxPublisher`가 1초 주기로 폴링해 Redis 채널 `amr.events`로 발행하고 `PUBLISHED`로 마킹한다 — 로봇 상태 보고(고빈도)는 스로틀링 설계가 아직 없어 이번 범위에서 제외했다(아래 열린 질문 참고 — 30단계에서 `robot.state_changed`/`robot.position`으로 나눠 해소).

**16단계 구현**: 같은 `OutboxPublisher`가 같은 이벤트를 Kafka 토픽 `amr.events`(파티션 1, 복제 1)에도 함께 발행한다 — Redis는 실시간 팬아웃(SSE), Kafka는 나중에 다른 시스템이 재구독/재생할 수 있는 내구성 있는 이벤트 로그 역할이다. 이 앱 내부에는 Kafka 컨슈머가 없다(프로듀서만).

**19단계에서 발견한 버그**: `OutboxPublisher`가 PENDING 이벤트를 순서대로 발행하는 반복문 전체를 하나의 `try` 없이 돌려서, 이벤트 하나(예: Kafka 브로커 unreachable)가 예외를 던지면 그 뒤에 있는 모든 PENDING 이벤트가 매 주기(1초)마다 영원히 처리되지 못하는 head-of-line 블로킹이 있었다. 지금은 이벤트별로 발행을 시도하고 실패한 것만 PENDING으로 남겨(다음 주기 재시도) 나머지는 계속 처리한다.

**31단계 — 다중 인스턴스와 보관 정책**:
- `OutboxPublisher`는 PENDING 행을 `SELECT ... FOR UPDATE SKIP LOCKED`로 100건씩 잠그며 가져온다. 서버를 여러 대 띄워도 다른 인스턴스가 잡은 행은 건너뛰어 같은 이벤트를 중복 발행하지 않는다. 배치마다 트랜잭션을 나눠, Kafka 응답을 기다리는 동안 DB 커넥션과 행 잠금을 쥐는 시간을 배치 하나로 제한한다. 배치가 가득 차고 실패가 없으면 같은 주기 안에서 다음 배치를 이어서 처리한다(한 주기 최대 20배치).
- 발행 완료(PUBLISHED)된 지 `app.retention.days`(기본 30일)가 지난 `outbox_events`는 매일 지운다(`DataRetentionService`). 그보다 오래 끊겼던 클라이언트의 `Last-Event-ID` 백로그는 재생할 수 없으니, 재접속 후 목록을 다시 조회한다. Kafka 토픽에는 남는다.

**27단계에서 수정한 신뢰성 문제 2가지**:

1. **Kafka 발행 실패를 성공으로 기록하던 문제**. `KafkaTemplate.send()`는 비동기라 브로커가 거부하거나 타임아웃돼도 예외 없이 실패한 future만 돌려준다. 예전엔 결과를 보지 않아 Kafka에 실제로 도착하지 않은 이벤트도 `PUBLISHED`로 마킹됐다.
   - 지금은 `OutboxPublisher`가 전송 결과를 최대 5초 기다려 확인하고, 실패하면 `PENDING`으로 남겨 재시도한다.
   - 발행 순서도 **Kafka → Redis**로 바꿨다. Kafka 실패로 재시도되는 이벤트가 SSE 클라이언트에 두 번 가지 않게 하기 위해서다.
2. **SSE 재접속 시 이벤트가 사라지던 문제**. `Last-Event-ID` 백로그를 DB에서 조회·전송한 **뒤에** Redis 구독을 등록해서, 그 사이에 발행된 이벤트는 어느 쪽에도 잡히지 않았다.
   - 지금은 구독을 먼저 등록한다. 백로그를 재생하는 동안 들어온 실시간 이벤트는 버퍼에 쌓았다가 재생이 끝난 뒤 보낸다.
   - 백로그로 이미 보낸 id는 건너뛴다(`SseEventRelay`).
   - 남은 한계: Kafka 재시도 등으로 Redis에 id 순서가 뒤바뀌어 도착하면(예: 6이 5보다 먼저), 연결된 클라이언트는 둘 다 받지만 그 사이에 끊긴 클라이언트는 `Last-Event-ID=6`으로 재접속해 5를 놓칠 수 있다. 정수 id 커서 방식의 한계라 클라이언트는 재접속 후 필요한 목록을 다시 조회하는 것을 권장한다.

**28단계에서 브라우저로 검증하다 발견해 고친 문제 2가지**:

1. **단계 진행 이벤트가 없었음**. 로봇 보내기·하역 완료처럼 작업 상태가 `RUNNING` 그대로이고 단계만 바뀌는 동작은 `task_events`가 남지 않아 outbox 이벤트도 없었다. 그래서 다른 화면이 실시간으로 갱신되지 않았다.
   - 지금은 단계가 시작될 때마다 `task.step_started` 이벤트를 발행한다. `payload`는 단계 유형(예: `"CONFIRM_UNLOAD"`)이다.
   - outbox에만 기록하고 `task_events`(상태 전이 이력)에는 넣지 않는다.
2. **연결해도 첫 이벤트 전까지 응답 헤더가 전송되지 않았음**. 그래서 브라우저 `EventSource`의 `onopen`이 불리지 않았다. 지금은 연결 직후 SSE 주석 한 줄(`:connected`)을 보내 헤더를 바로 내보낸다. 클라이언트는 주석을 무시한다.

**30단계 — 로봇 이벤트 2종**: 고빈도 상태 보고를 그대로 흘리지 않고 성격에 따라 둘로 나눴다.

| eventType | 경로 | 언제 | payload | eventId / 재접속 백로그 |
|---|---|---|---|---|
| `robot.state_changed` | outbox → Redis·Kafka (기존 이벤트와 같음) | 연결·동작·배터리·위치추정·적재 상태가 **실제로 바뀔 때**, 운영 상태(lifecycle) 변경 시. 하트비트(수신 시각만 갱신)는 기록하지 않는다 | `null` — 화면은 이 이벤트를 받으면 대시보드 개요·로봇 목록을 다시 조회한다 | 있음 / 재생됨 |
| `robot.position` | 시뮬레이터가 Redis 채널에 **직접** 발행(outbox·Kafka 우회) | 이동 명령이 성공한 뒤, 이전 노드에서 목적지 노드까지 활성 지도 통로 그래프의 최단 경로(Dijkstra)를 따라 200ms마다(기본 속도 초당 20 단위) | `{"mapVersionId":3,"x":42.0,"y":50.0,"arrived":false}` | **없음(`eventId: null`)** / 재생 안 됨 — SSE `id`도 붙이지 않아 `Last-Event-ID`에 영향이 없다 |

- `robot.position`은 **표시 전용**이다. 시뮬레이터 명령은 4단계부터 즉시(동기) 완료되고, 그 계약은 바뀌지 않는다. 명령이 커밋된 뒤 화면용 애니메이션 좌표를 흘려보낼 뿐이다. 로봇의 논리적 위치는 `robot_current_states.current_node_id`가 기준이다.
- 출발 노드를 모르면(첫 이동) 애니메이션 없이 목적지 노드에 바로 놓는다. 활성 지도나 작업장-노드 바인딩이 없으면 위치는 null이다.
- 경로 계산은 서버의 배정·작업 로직에 쓰이지 않는다(DESIGN.md §3 책임 분리 — 경로 추종은 로봇의 몫). 시뮬레이터가 "실제 로봇이라면 내부에서 했을" 이동을 흉내 낼 때만 쓴다. 24단계 검증이 활성 지도의 통로가 장애물을 지나지 않음을 보장하므로 경로는 자동으로 장애물을 피한다.
- MQTT 게이트웨이는 `robot.position`을 보내지 않는다(VDA5050 축소 구현에 위치 보고가 없다).

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/events/stream` | SSE 스트림. `Last-Event-ID` 헤더로 재접속 시 그 이후 이벤트를 먼저 재생한 뒤 실시간 구독으로 전환한다. **`siteId`/`stationId` 쿼리 필터는 미구현** — 전체 이벤트를 그대로 전달하므로 클라이언트가 필요하면 걸러 써야 한다 |

**19단계 — 인증**: 브라우저 네이티브 `EventSource`는 커스텀 헤더(`Authorization`)를 보낼 수 없다. 이 경로에 한해서만 `TokenAuthenticationFilter`가 `Authorization` 헤더가 없을 때 `access_token` 쿼리 파라미터를 대체 토큰 소스로 허용한다(다른 모든 엔드포인트는 그대로 헤더만 받는다). **트레이드오프**: 접근 토큰이 URL에 노출된다(서버 로그, 브라우저 히스토리 등) — 내부 관리자 도구 성격상 허용했지만, 프로덕션에서 더 넓게 쓰려면 단기 전용 스트림 토큰 발급 같은 방식으로 교체해야 한다. 프론트엔드는 관리자 화면 `/events`에서 `new EventSource(\`/api/events/stream?access_token=${token}\`)`로 사용한다.

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
- **19단계 수정**: 처음엔 백로그 재생 메시지에만 SSE `id`를 붙이고 실시간 구독 메시지엔 안 붙였다 — 그러면 한동안 라이브로만 받은 클라이언트가 재접속할 때 `Last-Event-ID`가 오래된 값에 머무는 버그가 있었다. 지금은 `EventStreamController`가 라이브 메시지도 페이로드를 파싱해 `eventId`로 `id`를 붙인다.

### 열린 질문 (미구현 남은 부분)

- ~~로봇 상태 보고(고빈도)를 이 스트림에 포함하는 것~~ → 30단계에서 아래처럼 나눠 해소했다.
- `siteId`/`stationId` 쿼리 필터 — `outbox_events`에 siteId가 없어 Task를 다시 join해야 한다.
- Kafka 토픽의 파티션/복제 전략 — 지금은 단일 브로커 개발 환경 기준 파티션 1·복제 1. 운영 규모에서 재검토 필요.

---

## 6. 외부 시스템 연동 (WMS/WCS 등)

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/external/tasks` | 작업 접수 (`Idempotency-Key` 또는 `externalRef` 필수) |
| GET | `/api/external/tasks/{externalRef}` | 상태/결과 조회 |

**9단계 구현**: 이 두 엔드포인트는 실제 WMS/WCS가 정해지기 전에도 성립하는 최소 접수/조회 계약이라 구현했다 — `POST`는 `Idempotency-Key` 헤더와 바디의 `externalRef` 중 하나만 있으면 되고(작업자 API와 달리 헤더 필수 아님), 재요청 시 둘 중 어느 키로든 같은 작업이면 200 + 기존 리소스를 반환한다(멱등). `GET`은 `externalRef`로 조회해 상태 폴링에 쓴다.

**26단계 — 인증**: 9단계부터 이 경로는 인증 방식이 미정이라 permitAll이었고, 누구나 로봇 작업을 만들 수 있었다. 실제 연동 대상과 무관하게 성립하는 최소 방식으로 **공유 API 키**를 도입했다.

```
POST /api/external/tasks
X-API-Key: <EXTERNAL_API_KEY 값>
```

- 서버 설정 `app.external.api-key`(환경 변수 `EXTERNAL_API_KEY`)와 상수 시간 비교로 검증하고, 일치하면 `ROLE_EXTERNAL`을 부여한다(`ExternalApiKeyFilter`). 키가 없거나 틀리면 401.
- **fail-closed**: 서버에 키가 설정돼 있지 않으면 어떤 요청도 인증되지 않는다. 운영에서 설정을 빠뜨려도 API가 열리지 않는다. 개발용 기본 키는 `application-local.yaml`에만 있다.
- 이 경로는 사용자 토큰(`Authorization: Bearer`)을 받지 않는다. 관리자 토큰으로도 호출할 수 없다.
- 한계: 연동 시스템이 여러 개여도 키가 하나다(시스템별 키·회전·폐기 없음). 실제 WMS/WCS가 정해지면 시스템별 자격증명(또는 mTLS/OAuth2 client credentials)으로 교체한다.

결과 회신 방식 중 **웹훅은 미구현** — DESIGN.md의 "기존 WMS/WCS 유무" 미확정 항목과 연동돼 있어(콜백 URL/인증 방식이 실제 대상에 따라 달라짐), 실제 연동 대상이 정해진 뒤 확정한다. 폴링(`GET`)만으로 "외부 요청부터 결과 회신까지 추적"은 가능하다.

리프트/컨베이어 등 설비 인터록·WMS 재고 연동은 여전히 미구현이다 — 어떤 벤더/시스템을 쓸지가 안 정해진 조달 결정이라 임의로 스펙을 만들지 않는다. 로봇 통신은 18단계에서 MQTT/VDA5050 어댑터가 옵트인으로 추가됐다(§7 참고) — 다만 이는 실제 로봇 벤더 선정과는 별개다: 여전히 실제 로봇은 없고, VDA5050을 지원하지 않는 벤더가 선정되면 `RobotGateway` 포트의 또 다른 구현체로 교체하면 된다.

---

## 7. 가상 로봇(시뮬레이터) — 이 문서의 범위 밖

DESIGN.md §1/§4는 "로봇 없는 개발: 실제 통신 계약을 모사하는 가상 로봇"을 4단계(로봇 어댑터·시뮬레이터) 필수 요소로 명시한다. 이 API 문서에는 시뮬레이터용 엔드포인트가 없는데, 의도적인 제외다.

- 시뮬레이터(가상 로봇)는 관리자/작업자가 쓰는 이 REST API가 아니라, **서버가 실제 로봇과 통신할 때 쓸 로봇 통신 채널**(§DESIGN.md 미확정 항목: MQTT/VDA 5050/제조사 API)을 통해 서버에 접속하는 것으로 가정한다. 즉 시뮬레이터는 "가짜 관리자/작업자"가 아니라 "가짜 로봇"이다.
- **4단계 구현 결과**: 로봇 통신 규격이 아직 미정이라, 실제 네트워크/브로커 대신 `RobotGateway` 인터페이스(포트) + `SimulatorRobotGateway`(인프로세스 어댑터)로 구현했다. `TaskService`가 MOVE 타입 단계를 시작할 때 `RobotCommand`를 만들어 이 게이트웨이에 넘기면, 시뮬레이터가 같은 트랜잭션 안에서 즉시 SENT→ACCEPTED→RUNNING→SUCCEEDED(또는 FAILED/UNKNOWN)까지 처리하고 `command_events`/`robot_current_states`/`robot_state_snapshots`를 기록한다. 실제 로봇 통신 규격이 정해지면 같은 `RobotGateway` 인터페이스의 다른 구현체(MQTT 등)로 교체하면 된다 — `TaskService`나 이 문서의 다른 API는 바뀌지 않는다.
- 관리자가 시뮬레이터에 장애를 주입하는 기능은 운영 전용 엔드포인트로 추가했다: `POST /api/admin/robots/{robotId}/simulator/faults` (body: `{"type": "COMMAND_FAILURE" | "COMMUNICATION_LOSS"}`) — 해당 로봇에 대한 다음 명령 디스패치 한 번에만 적용되고 소비된다. 실제 제품 기능이 아니라 테스트/운영 보조 도구이며, 메모리에만 저장돼 재시작 시 초기화된다.
- 로봇 상태 조회는 §2.4에 이미 정의된 `GET /api/admin/robots/{robotId}/state`, `GET /api/admin/robots/{robotId}/state-history`를 그대로 사용한다 (4단계에서 실제 구현).

### MQTT/VDA 5050 어댑터 (18단계, 옵트인)

`RobotGateway`의 두 번째 구현체 `MqttRobotGateway`를 추가했다 — `app.robot-gateway.mqtt.enabled=true`(기본 `false`)일 때만 활성화되고, 꺼져 있으면 지금처럼 `SimulatorRobotGateway`가 그대로 쓰인다. 실제 로봇은 여전히 없어 로컬 MQTT 브로커(Mosquitto)와 테스트용 "가상 VDA5050 로봇"으로만 검증했다.

- **토픽**: 로봇당 `uagv/v2/{manufacturer}/{serialNumber}/order`(발행)와 `.../state`(구독) 두 개만 쓴다. `connection` 토픽(LWT 기반 온라인·오프라인 알림)은 이번 범위에 없다.
- **VDA5050 축소 구현**: `order`는 목적지 station 하나짜리 단일 노드(`nodes: [{nodeId, sequenceId, released}]`)만 담는다 — edges/actions 등 전체 스펙의 나머지는 구현하지 않았다. `state`는 `orderId`/`driving`/`errors`/`batteryState.batteryCharge`만 본다. `driving:false`(또는 `errors` 존재)인 state가 오면 주문이 끝난 것으로 판단한다.
- **동기식-타임아웃 한계**: `RobotGateway.dispatch()`는 계약상 동기식이지만 실제 MQTT 통신은 비동기다. 이 어댑터는 주문을 발행한 뒤 일치하는 `orderId`의 state를 **3초** 동안 기다렸다가 결과를 반환한다 — 3초 안에 못 받으면 `UNKNOWN`(로봇 상태는 OFFLINE으로 보고돼 14단계 `ROBOT_COMM_LOST` 알람과 그대로 연동된다). **실제 로봇은 응답이 이보다 훨씬 늦을 수 있어 이 방식은 프로덕션에 적합하지 않다** — 진짜 비동기 처리로 전환하려면 `TaskService`의 명령 완료 처리 흐름 자체를 재설계해야 한다(이번 범위 밖).
- **설정 적용은 미구현**: `applyConfiguration()`은 시뮬레이터와 동일하게 즉시 성공 처리만 한다 — 실제 로봇에 설정을 적용하는 프로토콜은 정해지지 않았다.
- 관련 환경 변수: `ROBOT_GATEWAY_MQTT_ENABLED`, `MQTT_BROKER_URL`(기본 `tcp://localhost:1883`), `MQTT_MANUFACTURER`(기본 `AMRProject`).

---

## 8. 열린 질문 (구현 전 확정 필요)

- ~~인증 방식~~ — 10단계에서 자체 서명 토큰으로 확정(§1 참고). 로봇 자체 인증 방식(게이트웨이 mTLS 등)은 여전히 미정 — 로봇 통신 규격 미정과 연동돼 있다. 외부 시스템(WMS/WCS) 인증은 26단계에서 임시로 공유 API 키를 도입했다(§6). 시스템별 자격증명 방식은 연동 대상이 정해진 뒤 확정한다.
- `report-out-of-stock` 이후 작업 진행 정책과 WMS 재고 반영 여부.
- ~~실시간 이벤트 팬아웃 인프라~~ — 15단계에서 Redis Pub/Sub으로 확정(§5 참고). Kafka는 팬아웃이 아니라 이벤트 로그 용도로 16단계에서 추가 예정.
- 외부 연동 결과 회신 방식(webhook/polling). 인증은 26단계에서 공유 API 키로 임시 확정했다.
- 관리자 "지도 검증(`validate`)"의 구체적 검증 규칙(연결성, 고립 노드, 폭 등) — 지도 데이터 형식이 정해진 뒤 상세화.
- 상태 전이 API의 "이미 완료된 요청은 200으로 현재 상태 반환" 규칙이 모든 전이 액션에 예외 없이 적용 가능한지 — 예: `report-out-of-stock`처럼 부작용이 큰 액션도 재요청 시 아무 일 없다는 듯 200을 줘도 되는지는 액션별로 재검토 필요.
