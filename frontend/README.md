# AMR 관제 시스템 — 프론트엔드

AMRProject 백엔드(Spring Boot)를 위한 관리자·작업자 화면. React 19 + Vite + TypeScript + Tailwind CSS v4 + React Router + TanStack Query.

프로젝트 전체 개요와 백엔드 실행 방법은 저장소 루트의 [README.md](../README.md)를 참고한다.

## 진행 상태

| 단계 | 화면 변경 |
|---|---|
| 11~12 | 관리자 화면, 작업자 화면 |
| 19 | 알람 화면(`/alarms`), 실시간 이벤트 화면(`/events`) |
| 20 | 모든 화면을 얇은 페이지 셸 + `src/components/<domain>/` 소형 컴포넌트로 분리(기능 변경 없음) |
| 21 | 로봇 상세에 기본정보(이름/모델/제조사) 수정 폼 |
| 24 | 작업장 ↔ 지도 노드 연결 폼(`StationMapBindingForm`) |
| 25 | 지도 버전 상세를 SVG 캔버스 편집기(`MapCanvasEditor`)로 교체 |
| 28 | 역할별 라우트 가드·404, 전역 실시간 갱신(SSE), 한글 라벨, 확인창·토스트, 사이드바 알람 배지·모바일 메뉴, 작업자 화면 개선, CI에서 lint·build |
| 29 | 디스패치 화면 안내 문구(자동 실행 반영) |
| 31 | 작업·알람 목록 페이지네이션(`Pagination`), 사이드바 알람 수를 페이지 응답의 `totalElements`로 계산 |
| 30 | 관제 대시보드(`/dashboard`, 관리자 첫 화면) — KPI 타일, 로봇 상태표, 활성 지도 위 로봇 위치·이동 애니메이션. 로봇 목록에 연결·배터리 열 |

18단계(MQTT/VDA5050)는 순수 백엔드 인프라라 화면이 필요 없다.

## 실행

```bash
npm install
npm run dev
```

`http://localhost:5173`에서 열리며, `/api` 요청은 vite 프록시(`vite.config.ts`)를 통해 `http://localhost:8080`(백엔드)으로 전달된다. 백엔드가 먼저 떠 있어야 한다.

```bash
npm run build   # tsc -b && vite build — 프로덕션 빌드를 dist/에 생성
npm run preview # 빌드 결과 미리보기
npm run lint    # oxlint
```

CI(`.github/workflows/ci.yml`의 `frontend` job, 28단계)가 PR마다 `npm ci` → `npm run lint` → `npm run build`를 실행한다. 자동화된 화면 테스트는 없으므로, 화면을 바꾸면 실제 백엔드에 붙여 브라우저로 직접 확인한다(`../AGENTS.md`).

## 구조

| 경로 | 내용 |
|---|---|
| `src/lib/apiClient.ts` | `fetch` 기반 API 클라이언트, 토큰 첨부, 에러 응답 파싱(`ApiError`, `errorMessage`). 403은 전용 메시지로 바꾼다 |
| `src/lib/auth.tsx` | 로그인 상태 관리, `ProtectedRoute`(28단계: `roles`로 역할 검사), 역할별 기본 경로(`homePathFor`) |
| `src/lib/eventStream.tsx` | (28단계) `EventStreamProvider`/`useEventStream` — SSE 연결 하나를 레이아웃에서 공유하고, 이벤트 종류별로 TanStack Query 캐시를 무효화한다 |
| `src/lib/labels.ts` | (28단계) 백엔드 enum → 한글 라벨, 작업 상태 배지 색(`taskStatusTone`) |
| `src/lib/toast.tsx` | (28단계) `ToastProvider`/`useToast` — 버튼 동작의 성공·실패 피드백 |
| `src/lib/useSites.ts` | 물류센터 목록 조회 훅 |
| `src/components/dashboard/` | (30단계) `KpiTiles`, `RobotStatusTable`(+`BatteryBar`), `LiveMapView`(활성 지도 + 로봇 마커) |
| `src/types/api.ts` | 백엔드 응답 타입 (`docs/API.md`와 대응) |
| `src/components/ui.tsx` | 공용 UI 프리미티브 (버튼, 카드, 뱃지, 폼 요소, 테이블, 28단계 `ConfirmDialog`/`ConfirmButton`) |
| `src/components/auth/` | `LoginForm` |
| `src/components/sites/` | `SiteCreateForm`, `SiteTable`, `StationCreateForm`, `StationTable`, `StationMapBindingForm` |
| `src/components/robots/` | `RobotCreateForm`, `RobotTable`, `RobotBasicInfoForm`, `RobotLifecyclePanel`, `RobotStatePanel`, `RobotConfigForm`, `RobotConfigHistoryTable` |
| `src/components/maps/` | `MapCreateForm`, `MapTable`, `VersionCreateForm`, `VersionTable`, `MapVersionHeaderCard`, `MapCanvasEditor`(노드·장애물·통로 캔버스 편집), `ZonePanel` |
| `src/components/tasks/` | `TaskFilters`, `TaskTable`, `TaskActionsPanel`, `TaskStepsTable`, `TaskEventsTable` |
| `src/components/dispatch/` | `DispatchForm`, `DispatchResultSummary` |
| `src/components/users/` | `UserCreateForm`, `UserTable`, `StationAssignmentForm`, `StationAssignmentTable` |
| `src/components/worker/` | `CallRobotForm`, `WorkerTaskCard`, `StationCard` |
| `src/components/alarms/` | `AlarmFilters`, `AlarmTable`, `AlarmRow`, `alarmTone.ts`(배지 색 매핑) |
| `src/components/events/` | `EventFeed`, `EventFeedItem`, `ConnectionStatusBadge` |
| `src/layouts/AdminLayout.tsx` | 관리자 레이아웃 — 사이드바(미확인 알람 수 배지, 실시간 연결 상태), 모바일에서는 ☰ 버튼으로 여닫는다 |
| `src/layouts/WorkerLayout.tsx` | 작업자 레이아웃 — 상단바(실시간 연결 상태) |
| `src/pages/sites,robots,maps,tasks,dispatch,users` | 관리자 화면. 각 페이지는 `useQuery`/`useMutation` + 위 컴포넌트 조합만 담당하는 얇은 셸이다(20단계) |
| `src/pages/alarms/AlarmsPage.tsx` | 알람 화면 — 필터 + 목록, 행마다 `allowedActions` 기반 확인/해제 버튼 |
| `src/pages/events/EventsPage.tsx` | 실시간 이벤트 화면 — 레이아웃이 공유하는 SSE 연결의 이벤트를 최신순 피드로 표시 |
| `src/pages/worker` | 작업자 화면 — 내 작업장 목록, 작업장별 작업 카드(호출·보내기·하역 완료·작업 불가·호출 취소) |
| `src/pages/NotFoundPage.tsx` | (28단계) 없는 경로 |
| `src/pages/dashboard/DashboardPage.tsx` | (30단계) 관제 대시보드 — 물류센터 선택(브라우저에 기억), `GET /api/admin/sites/{siteId}/overview` |

## 라우팅

- `/login` — 로그인
- `/` 이하 (`AdminLayout`) — **`ADMIN`만**: `/dashboard`(30단계, `/`의 기본 이동 경로), `/sites`, `/robots`, `/maps`, `/tasks`, `/dispatch`, `/users`, `/alarms`, `/events`
- `/worker` 이하 (`WorkerLayout`) — `WORKER`와 `ADMIN`: `/worker/stations`, `/worker/stations/:stationId/tasks`
- 그 밖의 경로 — 404 화면

로그인 성공 시 역할에 따라 `/dashboard`(ADMIN, 30단계 전에는 `/sites`) 또는 `/worker/stations`(WORKER)로 이동한다(`homePathFor`). 28단계부터 역할이 맞지 않는 화면에 들어가면 자기 기본 화면으로 보낸다. 예전에는 WORKER가 주소창에 `/robots`를 입력하면 관리자 레이아웃이 뜨고 API만 403이었다. 화면 가드는 편의일 뿐이고 실제 권한은 서버가 검사한다(26단계 작업장 단위 접근 제어 포함).

## 실시간 갱신 (28단계)

- `AdminLayout`과 `WorkerLayout`이 각각 `EventStreamProvider`로 `GET /api/events/stream` 연결을 **하나만** 연다. 이벤트가 오면 `aggregateType`별로 쿼리를 무효화한다.
  - `TASK`: `["tasks"]`, `["worker"]`, `["overview"]`
  - `ALARM`: `["alarms"]`, `["overview"]`
  - `ROBOT`(30단계 `robot.state_changed`): `["overview"]`, `["robots"]`
  - 해당 데이터를 보여주는 화면이 열려 있으면 TanStack Query가 즉시 다시 조회한다.
- 19단계에는 `/events` 화면에서만 구독해서 다른 화면은 새로고침하거나 5초 폴링을 기다려야 했다. 이제 알람·작업자 화면의 폴링은 연결이 끊겼을 때를 위한 30초 안전장치만 남아 있다.
- 30단계 `robot.position`(시뮬레이터 이동 애니메이션 좌표, 초당 5번)은 쿼리를 무효화하지 않고 이벤트 목록에도 넣지 않는다. 별도 컨텍스트(`useRobotPositions`)에만 담아 `LiveMapView`만 다시 그린다 — 같은 컨텍스트에 두면 좌표가 올 때마다 레이아웃 전체가 다시 그려진다. 지도는 최근 3초 안에 받은 좌표가 있으면 그 좌표를, 없으면 개요 API의 `currentNodeId` 노드 위치를 쓴다.
- 새 aggregate 이벤트를 화면에 반영하려면 `src/lib/eventStream.tsx`의 `INVALIDATION_KEYS`에 쿼리 키를 추가한다.
- 브라우저 네이티브 `EventSource`는 커스텀 헤더를 못 보내 `?access_token=` 쿼리 파라미터로 토큰을 붙인다. 백엔드는 이 경로에 한해서만 이를 허용한다(19단계, `TokenAuthenticationFilter`). 토큰이 URL에 노출되는 트레이드오프는 `../docs/API.md` §5에 있다.

## 화면 규칙

- 버튼 노출은 응답의 `allowedActions` 배열만 보고 결정한다(`../docs/API.md`). 문서에 **미구현**으로 표시된 엔드포인트는 호출할 수 없으니, 화면 구현 전에 컨트롤러가 실제로 있는지 확인한다.
- 되돌릴 수 없는 동작(작업 취소, 최종 실패 처리, 재개, 로봇 운영 상태 변경, 작업자 호출 취소)은 `ConfirmButton`/`ConfirmDialog`로 한 번 더 확인받는다.
- 성공 피드백은 `useToast().showToast(...)`로 띄운다. 계속 보여야 하는 오류(폼 검증, 요청 실패 사유)는 `ErrorBanner`를 쓴다.
- enum 값은 화면에 그대로 노출하지 않고 `labels.*`로 바꿔 보여준다. 백엔드에 enum 값이 추가되면 `labels.ts` 매핑도 추가한다(없으면 원래 값이 그대로 보인다).
- 저장소 전체 개발 규칙: `../AGENTS.md`
