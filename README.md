# AMRProject — AMR/AGV 관제 시스템

물류센터에서 작업 요청을 받아 로봇을 배정하고, 이동·적재·하역·충전을 관리하는 AMR/AGV(자율주행 이동로봇) 관제 시스템. 백엔드부터 검증한 뒤 관리자 화면, 작업자 화면 순으로 구현했다 (`docs/DESIGN.md` §11 실제 구현 순서 기준).

## 구성

| 영역 | 내용 |
|---|---|
| 백엔드 | Spring Boot 4 / Java 21, MySQL 8.4, Flyway, Spring Security |
| 관리자 화면 | `frontend/` — React 19 + Vite + TypeScript, 지도·로봇·작업장·작업·디스패치·사용자 관리 |
| 작업자 화면 | 같은 프런트엔드 앱 내 `/worker` 라우트 — 로봇 호출, 보내기, 하역 완료, 작업 불가, 호출 취소 |
| 로봇 | 실제 장비 없이 통신 계약을 모사하는 가상 로봇(시뮬레이터)으로 개발·검증 |

구현 범위와 각 단계별 완료 기준은 `docs/DESIGN.md` §11에, API 계약은 `docs/API.md`에, 상태 전이는 `docs/STATE_MACHINES.md`에, 테이블 구조는 `docs/ERD.md`에 정리되어 있다.

## 사전 준비

- JDK 21
- Docker (MySQL 실행 및 통합 테스트용 Testcontainers)
- Node.js 20+ (프론트엔드)

## 백엔드 실행

```bash
# MySQL 기동
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
| `docs/API.md` | 엔드포인트별 요청/응답, `allowedActions` 계약 (알람·SSE 등 **미구현** 항목 표시 포함) |
| `docs/STATE_MACHINES.md` | 작업/로봇 등 상태 전이 다이어그램 (일부 **미구현** 표시 포함) |
| `docs/ERD.md` | 테이블 구조 (일부 **미구현** 표시 포함) |
| `AGENTS.md` | 이 저장소에서 작업할 때 지키는 개발 규칙(계층 구조, 변경 범위, 테스트 컨벤션 등) |

## 진행 상태

`docs/DESIGN.md` §11에 정의된 13단계 중 1~12단계(프로젝트 기반부터 작업자 화면까지)를 구현·검증 완료했다. 13단계(현장 검증)는 실제 로봇·실제 현장이 필요한 운영 활동으로, 이 저장소의 소프트웨어 구현 범위에는 포함하지 않는다.
