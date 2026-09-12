# AMR 관제 시스템 — 프론트엔드

AMRProject 백엔드(Spring Boot)를 위한 관리자·작업자 화면. React 19 + Vite + TypeScript + Tailwind CSS v4 + React Router + TanStack Query.

프로젝트 전체 개요와 백엔드 실행 방법은 저장소 루트의 [README.md](../README.md)를 참고한다.

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
| `src/types/api.ts` | 백엔드 응답 타입 (`docs/API.md`와 대응) |
| `src/components/ui.tsx` | 공용 UI 컴포넌트 (버튼, 카드, 뱃지, 폼 요소 등) |
| `src/layouts/AdminLayout.tsx` | 관리자 화면 레이아웃(사이드바 네비게이션) |
| `src/layouts/WorkerLayout.tsx` | 작업자 화면 레이아웃(단순 상단바) |
| `src/pages/sites,robots,maps,tasks,dispatch,users` | 관리자 화면 — 물류센터/작업장, 로봇, 지도, 작업, 디스패치, 사용자 관리 |
| `src/pages/worker` | 작업자 화면 — 내 작업장 목록, 작업장별 작업 카드(호출·보내기·하역 완료·작업 불가·호출 취소) |

## 라우팅

- `/login` — 로그인
- `/` 이하 (`AdminLayout`) — `ADMIN` 역할, `/sites`, `/robots`, `/maps`, `/tasks`, `/dispatch`, `/users`
- `/worker` 이하 (`WorkerLayout`) — `WORKER` 역할(관리자도 접근 가능), `/worker/stations`, `/worker/stations/:stationId/tasks`

로그인 성공 시 역할에 따라 `/sites`(ADMIN) 또는 `/worker/stations`(WORKER)로 자동 이동한다(`homePathFor`, `src/lib/auth.tsx`).

## 참고

- 백엔드 API 계약: `../docs/API.md` (응답의 `allowedActions` 배열로 화면에 노출할 버튼을 결정한다). 문서에 **미구현**으로 표시된 엔드포인트(알람, 로봇 기본정보 수정, SSE 실시간 이벤트 등)는 실제로 호출할 수 없다 — 화면 구현 전에 해당 컨트롤러가 실제로 있는지 확인한다. 실시간성이 필요한 화면은 현재 SSE 대신 TanStack Query `refetchInterval` 폴링을 쓴다 (예: `WorkerStationTasksPage`).
- 저장소 전체 개발 규칙: `../AGENTS.md`
