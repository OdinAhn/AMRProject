# AMR 관제 시스템 — 프론트엔드

AMRProject 백엔드(Spring Boot)를 위한 관리자·작업자 화면. React 19 + Vite + TypeScript + Tailwind CSS v4 + React Router + TanStack Query.

프로젝트 전체 개요와 백엔드 실행 방법은 저장소 루트의 [README.md](../README.md)를 참고한다.

**진행 상태**: 11~12단계(관리자 화면, 작업자 화면)에 더해 19단계(알람 화면, 실시간 이벤트 화면)까지 구현돼 있다. 17단계(로봇 기본정보 수정)와 18단계(MQTT/VDA5050)는 화면이 필요 없거나(MQTT는 순수 백엔드 인프라) 아직 화면을 안 붙였다 — 자세한 내용은 아래 "참고" 절 참고.

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

## 구조

| 경로 | 내용 |
|---|---|
| `src/lib/apiClient.ts` | `fetch` 기반 API 클라이언트, 토큰 첨부, 에러 응답 파싱(`ApiError`) |
| `src/lib/auth.tsx` | 로그인 상태 관리, `ProtectedRoute`, 역할별 로그인 후 이동 경로(`homePathFor`) |
| `src/lib/useEventStream.ts` | `GET /api/events/stream` 구독 훅(19단계) — `EventSource` 연결/상태/이벤트 목록 관리 |
| `src/types/api.ts` | 백엔드 응답 타입 (`docs/API.md`와 대응) |
| `src/components/ui.tsx` | 공용 UI 프리미티브 (버튼, 카드, 뱃지, 폼 요소, 테이블 등) |
| `src/components/alarms/` | 알람 화면 하위 컴포넌트 — `AlarmFilters`, `AlarmTable`, `AlarmRow`, `alarmTone.ts`(배지 색 매핑) |
| `src/components/events/` | 실시간 이벤트 화면 하위 컴포넌트 — `EventFeed`, `EventFeedItem`, `ConnectionStatusBadge` |
| `src/layouts/AdminLayout.tsx` | 관리자 화면 레이아웃(사이드바 네비게이션) |
| `src/layouts/WorkerLayout.tsx` | 작업자 화면 레이아웃(단순 상단바) |
| `src/pages/sites,robots,maps,tasks,dispatch,users` | 관리자 화면 — 물류센터/작업장, 로봇, 지도, 작업, 디스패치, 사용자 관리 |
| `src/pages/alarms/AlarmsPage.tsx` | 알람 화면(19단계) — 필터 + 목록, 행마다 `allowedActions` 기반 확인/해제 버튼 |
| `src/pages/events/EventsPage.tsx` | 실시간 이벤트 화면(19단계) — SSE로 들어오는 이벤트를 최신순 피드로 표시 |
| `src/pages/worker` | 작업자 화면 — 내 작업장 목록, 작업장별 작업 카드(호출·보내기·하역 완료·작업 불가·호출 취소) |

## 라우팅

- `/login` — 로그인
- `/` 이하 (`AdminLayout`) — `ADMIN` 역할, `/sites`, `/robots`, `/maps`, `/tasks`, `/dispatch`, `/users`, `/alarms`, `/events`
- `/worker` 이하 (`WorkerLayout`) — `WORKER` 역할(관리자도 접근 가능), `/worker/stations`, `/worker/stations/:stationId/tasks`

로그인 성공 시 역할에 따라 `/sites`(ADMIN) 또는 `/worker/stations`(WORKER)로 자동 이동한다(`homePathFor`, `src/lib/auth.tsx`).

## 참고

- 백엔드 API 계약: `../docs/API.md` (응답의 `allowedActions` 배열로 화면에 노출할 버튼을 결정한다). 문서에 **미구현**으로 표시된 엔드포인트는 실제로 호출할 수 없다 — 화면 구현 전에 해당 컨트롤러가 실제로 있는지 확인한다.
- **실시간 이벤트(`/events`)는 브라우저 네이티브 `EventSource`를 쓴다** — 커스텀 헤더를 못 보내 `Authorization` 대신 `?access_token=` 쿼리 파라미터로 토큰을 붙인다(`useEventStream.ts`). 백엔드가 이 경로에 한해서만 쿼리 파라미터 토큰을 허용한다(19단계, `TokenAuthenticationFilter`) — 토큰이 URL에 노출되는 트레이드오프는 `../docs/API.md` §5에 명시. 기존 화면들(작업 목록 등)은 여전히 TanStack Query `refetchInterval` 폴링을 쓴다 — 전면 SSE 전환은 하지 않았다.
- 로봇 기본정보 수정 API(`PATCH /api/admin/robots/{robotId}`, 17단계)는 백엔드엔 있지만 아직 화면이 없다. MQTT/VDA5050(18단계)은 순수 백엔드 인프라라 애초에 화면이 필요 없다.
- 저장소 전체 개발 규칙: `../AGENTS.md`
