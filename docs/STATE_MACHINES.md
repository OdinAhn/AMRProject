# 상태 전이 다이어그램 (v0)

`DESIGN.md` §6, `ERD.md`의 상태 컬럼(status)과 1:1로 대응한다. 다이어그램에 없는 상태값이나 전이가 코드에 생기면 이 문서부터 갱신한다.

---

## 1. 작업 (Task)

```mermaid
stateDiagram-v2
    [*] --> QUEUED
    QUEUED --> ASSIGNED : 로봇 배정 성공
    QUEUED --> CANCELED : 취소 요청 (배정 전, 즉시 처리)

    ASSIGNED --> RUNNING : 첫 명령 ACCEPTED
    ASSIGNED --> CANCEL_REQUESTED : 취소 요청
    ASSIGNED --> RECOVERY_REQUIRED : 배정 직후 통신 두절/명령 전송 실패

    RUNNING --> SUCCEEDED : 모든 단계 SUCCEEDED
    RUNNING --> PAUSED : 일시정지 (관리자/안전정지 연동)
    RUNNING --> CANCEL_REQUESTED : 취소 요청
    RUNNING --> RECOVERY_REQUIRED : 명령 실패/통신 단절/서버 재시작 후 대조 실패

    PAUSED --> RUNNING : 재개
    PAUSED --> CANCEL_REQUESTED : 취소 요청

    CANCEL_REQUESTED --> CANCELED : 취소 완료 확인
    CANCEL_REQUESTED --> RECOVERY_REQUIRED : 취소 처리 중 실패/불확실

    RECOVERY_REQUIRED --> RUNNING : 상태 대조 후 정상 재개
    RECOVERY_REQUIRED --> FAILED : 복구 불가, 운영자 개입 확정

    SUCCEEDED --> [*]
    CANCELED --> [*]
    FAILED --> [*]
```

| From | To | Trigger | Guard / 비고 |
|---|---|---|---|
| QUEUED | ASSIGNED | 배정기가 로봇 선택 성공 | 동시 배정 방지 트랜잭션 필요 (ERD §9) |
| QUEUED | CANCELED | 작업자/관리자 취소 | 물리 동작 없음 → 중간 상태 없이 즉시 종료 |
| ASSIGNED | RUNNING | 첫 `robot_commands`가 ACCEPTED | |
| ASSIGNED | CANCEL_REQUESTED | 취소 요청 | 아직 적재 전이므로 배정 해제만으로 취소 가능한 경우가 많음 |
| ASSIGNED | RECOVERY_REQUIRED | 배정 직후 명령 전송/응답 실패 | |
| RUNNING | SUCCEEDED | 마지막 단계 SUCCEEDED | |
| RUNNING | PAUSED | 관리자 일시정지, 로봇 PROTECTIVE_STOP 등 | 화면 정지 요청과 로봇 실제 안전 정지는 별도 축(§4 참고) — task.PAUSED는 업무 흐름 정지일 뿐, 로봇 안전 상태와 동일시하지 않는다 |
| RUNNING | CANCEL_REQUESTED | 취소 요청 | **미정**: 적재 완료 후(물품을 실은 채) 취소 가능 여부·절차는 정책 미확정. 하역 없이 취소하면 물품이 로봇에 남는 문제 — 구현 전 결정 필요 |
| RUNNING | RECOVERY_REQUIRED | 명령 실패, 통신 단절, 서버 재시작 후 로봇 실제 상태와 불일치 | 재시도 전 반드시 로봇/설비 실제 상태 확인 (DESIGN.md §9) |
| PAUSED | RUNNING | 재개 | |
| PAUSED | CANCEL_REQUESTED | 취소 요청 | |
| CANCEL_REQUESTED | CANCELED | 로봇이 안전하게 정지/복귀 확인됨 | |
| CANCEL_REQUESTED | RECOVERY_REQUIRED | 취소 명령 응답 없음/실패 | |
| RECOVERY_REQUIRED | RUNNING | 상태 대조 결과 정상, 남은 단계부터 재개 | |
| RECOVERY_REQUIRED | FAILED | 자동 복구 불가, 운영자가 최종 실패 처리 | 감사 로그 필수 |

**종료 상태**: `SUCCEEDED`, `CANCELED`, `FAILED` — 이후 전이 없음.

---

## 2. 작업 단계 (Task Step)

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> RUNNING : 단계 시작 (명령 발행 또는 사용자 확인 대기 진입)
    RUNNING --> SUCCEEDED : 완료 확인 (명령 SUCCEEDED 또는 버튼 확인)
    RUNNING --> FAILED : 명령 FAILED, 복구 불가
    FAILED --> RUNNING : 관리자 RESUME 재시도 (5단계, DESIGN.md §9)
    PENDING --> SKIPPED : 정책상 불필요 단계로 판단
    SUCCEEDED --> [*]
    SKIPPED --> [*]
```

| From | To | Trigger | Guard / 비고 |
|---|---|---|---|
| FAILED | RUNNING | 관리자가 `POST /api/admin/tasks/{taskId}/recover`에 `RESUME` 요청 | 상위 Task가 `RECOVERY_REQUIRED`일 때만 가능. 같은 단계에 새 `robot_commands`를 발행해 재시도한다 (§3의 "활성 명령 최대 1개" 규칙은 이전 명령이 `FAILED`로 종료돼 있어 위반하지 않음) |

- 단계는 `task_steps.step_no` 순서대로 진행하되, 서버가 동시에 여러 단계를 `RUNNING`으로 열지 않는다 (한 번에 하나의 활성 단계).
- 단계 상태 변화는 상위 Task 상태에 다음과 같이 반영된다.
  - 첫 단계가 `RUNNING` → Task `RUNNING`
  - 마지막 단계가 `SUCCEEDED` → Task `SUCCEEDED`
  - 임의 단계가 `FAILED` → Task `RECOVERY_REQUIRED` (즉시 `FAILED` 아님 — 운영자/복구 로직이 먼저 판단)
- `SKIPPED`는 현재 설계 범위에서 사용처가 확정되지 않음 — 조건부 단계(예: 목적지가 충전소라 하역 단계가 없는 경우) 도입 시 활용, 아니라면 초기 구현에서 제외 가능.

---

## 3. 로봇 명령 (Robot Command)

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> SENT : 게이트웨이로 송신
    SENT --> ACCEPTED : 로봇 수락 응답
    SENT --> FAILED : 로봇 거부 응답
    SENT --> UNKNOWN : 응답 타임아웃 (수신 여부 불명)
    ACCEPTED --> RUNNING : 실행 시작 보고
    RUNNING --> SUCCEEDED : 완료 보고
    RUNNING --> FAILED : 실패 보고
    RUNNING --> UNKNOWN : 진행 중 통신 단절
    UNKNOWN --> RUNNING : 재연결 후 "진행 중" 확인
    UNKNOWN --> SUCCEEDED : 재연결 후 "이미 완료" 확인
    UNKNOWN --> FAILED : 재연결 후 "실패/미실행" 확인 및 재시도 결정
    SUCCEEDED --> [*]
    FAILED --> [*]
```

- `UNKNOWN`은 DESIGN.md §6의 "응답이 없어서 실제 실행 여부를 모르는 경우"에 대응하는 상태다. **`UNKNOWN`을 임의로 `FAILED`로 간주해 재전송하지 않는다** — 반드시 로봇/게이트웨이에 실제 상태를 재조회한 뒤에만 다음 상태로 전이한다.
- `command_events`에 모든 전이가 기록되며, `occurred_at`(로봇 보고 시각)과 `received_at`(서버 수신 시각)을 분리해 지연·유실을 감지한다.
- 하나의 `task_step`에 대해 활성(`PENDING`~`RUNNING`) 명령은 최대 1개만 존재해야 한다 (중복 명령 방지, DESIGN.md §9).

---

## 4. 로봇 상태 (다축, 독립 상태)

로봇 상태는 단일 FSM이 아니라 **서로 독립적인 축**이다 (`robot_current_states`/`robot_state_snapshots` 컬럼과 대응). 각 축은 별도로 전이되며 동시에 여러 값을 가진다 (예: `연결=ONLINE`, `동작=MOVING`, `안전=NORMAL`가 동시에 성립).

### 4.1 연결 (connection_state)

```mermaid
stateDiagram-v2
    [*] --> OFFLINE
    OFFLINE --> ONLINE : 최초 연결/재연결
    ONLINE --> STALE : 하트비트 지연 (임계 시간 초과)
    STALE --> ONLINE : 하트비트 재수신
    STALE --> OFFLINE : 연결 완전 끊김 (타임아웃)
    ONLINE --> OFFLINE : 연결 끊김 감지
```

### 4.2 운전 모드 (operation_mode)

```mermaid
stateDiagram-v2
    [*] --> MANUAL
    MANUAL --> AUTO : 자동 모드 전환 (서버 명령 수신 가능)
    AUTO --> MANUAL : 수동 전환 (현장 조작)
    AUTO --> MAINTENANCE : 점검 모드 진입
    MANUAL --> MAINTENANCE : 점검 모드 진입
    MAINTENANCE --> MANUAL : 점검 종료
```

- `AUTO`가 아니면 서버는 신규 명령을 보내지 않는다 (배정 대상에서 제외).

### 4.3 동작 (motion_state)

```mermaid
stateDiagram-v2
    [*] --> IDLE
    IDLE --> MOVING : 이동 명령 수락
    MOVING --> IDLE : 목적지 도착/정지
    MOVING --> DOCKING : 도킹 지점 접근
    DOCKING --> IDLE : 도킹 완료/취소
    IDLE --> CHARGING : 충전 시작
    CHARGING --> IDLE : 충전 종료
```

### 4.4 안전 (safety_state)

```mermaid
stateDiagram-v2
    [*] --> NORMAL
    NORMAL --> PROTECTIVE_STOP : 장애물 감지 등 보호정지
    PROTECTIVE_STOP --> NORMAL : 장애 해제, 자동 복귀
    NORMAL --> EMERGENCY_STOP : 비상정지 버튼/충돌
    PROTECTIVE_STOP --> EMERGENCY_STOP : 상황 악화
    EMERGENCY_STOP --> NORMAL : 현장 수동 해제 (원격 해제 불가 가정)
```

- **화면의 "정지 요청" 성공과 이 `safety_state`는 별개다.** 서버는 정지 명령을 SENT/ACCEPTED까지만 확인할 수 있고, 실제 `EMERGENCY_STOP`/`PROTECTIVE_STOP` 여부는 로봇이 보고하는 이 값으로만 판단한다 (DESIGN.md §3).

### 4.5 적재 (load_state)

```mermaid
stateDiagram-v2
    [*] --> EMPTY
    EMPTY --> LOADED : 적재 확인
    LOADED --> EMPTY : 하역 확인
    EMPTY --> UNKNOWN : 센서 응답 불가
    LOADED --> UNKNOWN : 센서 응답 불가
    UNKNOWN --> EMPTY : 재확인 결과 반영
    UNKNOWN --> LOADED : 재확인 결과 반영
```

### 4.6 위치 추정 (localization_state)

```mermaid
stateDiagram-v2
    [*] --> LOCALIZED
    LOCALIZED --> LOST : 위치 추정 실패
    LOST --> LOCALIZED : 재추정 성공
    LOCALIZED --> UNKNOWN : 보고 두절
    LOST --> UNKNOWN : 보고 두절
    UNKNOWN --> LOCALIZED : 재연결 후 확인
    UNKNOWN --> LOST : 재연결 후 확인
```

- `LOST` 또는 `UNKNOWN`인 로봇은 신규 이동 명령 배정에서 제외한다.

---

## 5. 충전 세션 (Charging Session)

```mermaid
stateDiagram-v2
    [*] --> RESERVED
    RESERVED --> CHARGING : 충전소 도착, 충전 시작
    RESERVED --> ABORTED : 예약 취소 (배터리 회복/우선순위 변경 등)
    CHARGING --> COMPLETED : 목표 배터리 도달
    CHARGING --> ABORTED : 강제 중단 (긴급 작업 배정 등)
    COMPLETED --> [*]
    ABORTED --> [*]
```

---

## 6. 자원 예약 (Resource Reservation — 통로/구역/정류지점)

```mermaid
stateDiagram-v2
    [*] --> HELD
    HELD --> RELEASED : 로봇이 자원 통과/사용 완료, 명시적 반납
    HELD --> EXPIRED : TTL 초과 (반납 누락/장애 대응)
    RELEASED --> [*]
    EXPIRED --> [*]
```

- `EXPIRED`는 통신 단절 등으로 반납되지 않은 예약이 교통을 영구 차단하지 않도록 하는 안전장치다. TTL 값과 만료 시 실제 로봇 위치 재확인 절차는 7단계(다중 로봇 운영) 구현 시 확정한다.

---

## 7. 알람 (Alarm)

```mermaid
stateDiagram-v2
    [*] --> OPEN
    OPEN --> ACKNOWLEDGED : 운영자 확인
    ACKNOWLEDGED --> RESOLVED : 조치 완료
    OPEN --> RESOLVED : 자동 해소 (원인 소멸 감지)
    RESOLVED --> OPEN : 재발생 (alarm_events: REOPENED)
```

---

## 8. 작업 배정 (Task Assignment)

```mermaid
stateDiagram-v2
    [*] --> ACTIVE
    ACTIVE --> RELEASED : 재배정/취소로 배정 해제
    ACTIVE --> COMPLETED : 작업이 SUCCEEDED로 종료
    RELEASED --> [*]
    COMPLETED --> [*]
```

- 하나의 Task에 대해 동시에 `ACTIVE`인 배정은 **최대 1개**여야 하며, `tasks.current_assignment_id`가 그 row를 가리킨다.
- 재배정이 일어나면 기존 row는 `RELEASED`로 남기고 새 row를 `ACTIVE`로 생성한다 (덮어쓰지 않음 — 배정 이력 보존).

---

## 9. 지도 버전 (Map Version)

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> VALIDATED : 검증 통과
    DRAFT --> DRAFT : 검증 실패, 수정 후 재검증
    VALIDATED --> ACTIVE : 활성화 (같은 map의 기존 ACTIVE는 ARCHIVED로)
    VALIDATED --> DRAFT : 활성화 전 재검증 필요 (파일 재업로드 등)
    ACTIVE --> ARCHIVED : 새 버전 활성화로 교체
    ARCHIVED --> [*]
```

- 하나의 `maps.id`에 대해 `ACTIVE` 버전은 항상 최대 1개.
- `VALIDATED`가 아닌 버전은 `activate` 호출 시 거부한다 (API.md §2.3).

---

## 10. 물품 (Load)

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> LOADED : 적재 확인 ('로봇 보내기')
    LOADED --> DELIVERED : 하역 확인 ('하역 완료')
    PENDING --> EXCEPTION : '재고 없음'/'작업 불가' 보고
    LOADED --> EXCEPTION : 하역 실패, 물품 손상 등
    DELIVERED --> [*]
    EXCEPTION --> [*]
```

- `loads.status`와 로봇 `load_state`(§4.5)는 별개 축이다 — 연결 규칙은 ERD.md §4.8 참고.
- `EXCEPTION` 이후 처리(재시도/작업 취소로 이어지는지)는 DESIGN.md §5에서 이미 미정으로 표시된 항목과 동일한 미확정 지점이다.

---

## 11. 로봇 생명주기 (robots.lifecycle_status)

```mermaid
stateDiagram-v2
    [*] --> REGISTERED
    REGISTERED --> IN_SERVICE : 등록 완료, 설정 적용 확인
    IN_SERVICE --> MAINTENANCE : 점검 전환
    MAINTENANCE --> IN_SERVICE : 점검 종료
    IN_SERVICE --> DECOMMISSIONED : 폐기/철수
    MAINTENANCE --> DECOMMISSIONED : 폐기/철수
    DECOMMISSIONED --> [*]
```

- 이 상태는 관리자가 직접 설정하는 값이며, §4.2의 `operation_mode`(로봇이 실시간 보고하는 값)와는 **독립적**이다. 동기화 규칙은 ERD.md §4.4 참고.

---

## 12. 열린 질문 (구현 전 확정 필요)

- Task `RUNNING` 중(특히 물품 적재 후) 취소 요청 시 처리 절차 — 목적지까지 강제 이동 후 취소인지, 중간 지점에서 되돌리는지는 운반 장치/현장 정책에 달려 있어 미정.
- `TaskStep.SKIPPED` 사용 여부 — 조건부 단계 도입 전까지는 스키마에만 존재하고 로직에서 미사용으로 둘지 결정 필요.
- `safety_state.EMERGENCY_STOP` 해제를 원격으로 허용할지(현재는 현장 수동 해제만 가정) — 로봇 제조사 사양 확인 후 결정.
- 자원 예약 TTL 값과 만료 처리 시 로봇 위치 재확인 로직의 구체적 절차.
- `lifecycle_status`와 `operation_mode`가 어긋난 상태(예: `MAINTENANCE`인데 로봇은 `AUTO` 보고)가 일정 시간 지속되면 알람을 띄울지 여부.
- 지도 버전 검증 실패 사유를 저장할 컬럼(`map_versions.validation_errors` 등)이 필요한지 — 현재 ERD엔 없음, 검증 규칙 구체화 시 함께 결정.
- 충전 작업이 `tasks`/`task_steps`를 재사용한다면 위 Task/Task Step 다이어그램이 그대로 적용되는지, 충전 전용 규칙(예: 배터리 목표 도달을 별도 완료 조건으로 볼지)이 추가로 필요한지.
