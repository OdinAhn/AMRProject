# AGENTS.md

## Project Overview

AMRProject is an AMR/AGV (autonomous mobile robot) fleet control system: a Spring Boot backend plus a React admin/worker frontend. See `docs/DESIGN.md` for the full design (business scope, state model, failure policy, staged implementation order) and the project root `README.md` for how to run everything. `docs/API.md`, `docs/ERD.md`, and `docs/STATE_MACHINES.md` are the authoritative contracts for endpoints, tables, and state transitions — keep them in sync with code changes. `docs/FIELD_VALIDATION.md` is the stage-13 field rollout runbook — out of this repo's software scope (needs real hardware/site), so it's documentation only, never a target for code changes.

The backend is developed and verified without real robots: a simulator (`com.amrproject.robot`) mimics robot command/status behavior behind the same contract a real vendor integration would use.

### Tech Stack

Backend (`src/`):

- Java 21, Gradle
- Spring Boot 4.x, Spring MVC (webmvc), Spring Data JPA (Hibernate 7)
- Spring Security 7 — custom self-signed HMAC-SHA256 access tokens (`security/AccessTokenService`), not a JWT library
- MySQL 8.4, Flyway migrations
- Redis (Pub/Sub) — real-time event fanout for `GET /api/events/stream` (stage 15). Kafka (stage 16) — durable event log, not fanout. A single scheduled `OutboxPublisher` (`service/event/OutboxPublisher`) relays `outbox_events` rows to both; no `@KafkaListener` exists in this app (Kafka is producer-only here, for future external subscribers).
- Eclipse Paho (`org.eclipse.paho.client.mqttv3`) — MQTT client for `robot/mqtt/MqttRobotGateway` (stage 18), a second `RobotGateway` implementation speaking a reduced VDA 5050 subset. **Opt-in and off by default** (`app.robot-gateway.mqtt.enabled=false`) — `SimulatorRobotGateway` stays the active gateway unless explicitly enabled. See gotcha below for its synchronous-with-timeout design.
- Jackson 3 — note the package is `tools.jackson.*`, not `com.fasterxml.jackson.*`
- springdoc-openapi (Swagger UI / OpenAPI)
- JUnit 5, Testcontainers (MySQL, Redis via `GenericContainer` + `@ServiceConnection`, Kafka via `testcontainers-kafka`'s `KafkaContainer` + `@DynamicPropertySource` — see gotcha below) for integration tests, Spring Security Test. These three are shared by every test via `support/AbstractIntegrationTest`. A fourth container (Mosquitto) exists only in `robot/mqtt/MqttRobotGatewayTest`, started separately with its own `@DynamicPropertySource` — it's not added to the shared base since MQTT is opt-in and off for the rest of the suite.

Frontend (`frontend/`):

- React 19, Vite, TypeScript
- Tailwind CSS v4 (`@tailwindcss/vite`, no `tailwind.config.js`)
- React Router, TanStack Query
- No automated frontend test suite by deliberate choice — frontend changes are verified by running the real backend + a real MySQL instance and walking through the feature in a browser (see `frontend/README.md`). Type-checking (`tsc -b` via `npm run build`) and `oxlint` are still required.

Before making changes, inspect the existing project structure and follow established patterns rather than introducing new architectural patterns unnecessarily.

---

## General Rules

1. Prefer simple, readable, maintainable code over clever abstractions.
2. Follow existing project conventions before introducing new ones.
3. Do not make unrelated changes.
4. Do not modify generated files manually.
5. Do not add dependencies unless they are necessary.
6. When adding a dependency, explain why it is needed.
7. Preserve backward compatibility unless the task explicitly requires a breaking change.
8. Do not expose internal implementation details through API responses.
9. Never commit secrets, passwords, API keys, tokens, or credentials.
10. Do not silently change database schema or API contracts.

---

## Project Structure

The backend follows this layered structure under `com.amrproject`:

```
src/main/java/com/amrproject/
├── controller/
│   └── <domain>/   (alarm, user, site, map, robot, task, event — see below)
├── service/
│   └── <domain>/
├── repository/
│   └── <domain>/
├── domain/
│   └── entity/
│       └── <domain>/
├── dto/
│   ├── request/
│   │   └── <domain>/
│   └── response/
│       └── <domain>/
├── exception/
├── security/   (access token issuance/validation)
├── robot/      (RobotGateway port + SimulatorRobotGateway; robot/mqtt/ has the opt-in MQTT/VDA5050 adapter, stage 18)
└── config/
```

`controller`, `service`, `repository`, `domain/entity`, and `dto/request`/`dto/response` are all split into the same 7 domain sub-packages (22~23단계) so a class's location signals what it belongs to at a glance:

| 하위 패키지 | 대표 내용 |
|---|---|
| `alarm` | 알람 (`Alarm`, `AlarmEvent`, 상태/유형 enum) |
| `user` | 사용자, 역할, 작업장 배정 |
| `site` | 물류센터, 작업장, 작업장-지도 바인딩 |
| `map` | 지도, 버전, 노드, 통로, 구역 |
| `robot` | 로봇, 설정, 상태 보고, 명령(`RobotCommand`/`CommandEvent`), 충전 |
| `task` | 작업, 단계, 배정, 이력, 자원 예약(`ResourceReservation`) |
| `event` | 아웃박스 이벤트(Outbox → Redis/Kafka 릴레이) |

공통 기반 클래스는 하위 패키지로 옮기지 않고 루트에 둔다: `domain.entity.BaseAuditableEntity`, `dto.response.ErrorResponse`. 새 컨트롤러/서비스/엔티티/DTO/리포지토리를 추가할 때는 해당 도메인 하위 패키지에 넣는다 — 애매하면 그 클래스가 다루는 리소스나(컨트롤러) 그 클래스를 실제로 쓰는 서비스가 어느 도메인인지로 판단한다(예: `ResourceReservation`은 지도가 아니라 `TaskService`가 쓰므로 `task`; `DispatchController`/`DispatchService`도 배차 대상이 작업이므로 `task`).

The frontend lives entirely under `frontend/` (see `frontend/README.md` for its internal layout — `src/pages`, `src/layouts`, `src/lib`, `src/components`, `src/types`) and is a separate npm project; it is not part of the Gradle build.

### Responsibilities

#### Controller

- Handle HTTP requests and responses.
- Validate request parameters and request bodies.
- Delegate business logic to services.
- Do not contain business logic.
- Do not directly access repositories.

#### Service

- Contain business logic.
- Define transaction boundaries.
- Coordinate repositories and domain objects.
- Avoid HTTP-specific concerns.

#### Repository

- Handle persistence.
- Keep business logic out of repositories unless it is specifically query-related.

#### Entity

- Represent persistent domain state.
- Encapsulate domain behavior where appropriate.
- Avoid exposing entities directly through REST APIs.

#### DTO

- Use request DTOs for incoming API data.
- Use response DTOs for outgoing API data.
- Do not return JPA entities directly from controllers.

---

## REST API Conventions

Use standard HTTP semantics.

- `GET` — retrieve resources
- `POST` — create resources
- `PUT` — replace/update an entire resource
- `PATCH` — partially update a resource
- `DELETE` — delete a resource

Prefer resource-oriented URLs.

Good:

```
GET    /api/users/{id}
POST   /api/users
PATCH  /api/users/{id}
DELETE /api/users/{id}
```

Avoid action-oriented URLs when a resource-oriented design is possible.

Avoid:

```
POST /api/user/create
POST /api/user/updateName
POST /api/user/delete
```

Use appropriate HTTP status codes.

- `200 OK` — successful request
- `201 Created` — resource created
- `204 No Content` — successful request with no response body
- `400 Bad Request` — invalid request
- `401 Unauthorized` — authentication required/failed
- `403 Forbidden` — authenticated but not allowed
- `404 Not Found` — resource does not exist
- `409 Conflict` — resource/state conflict
- `500 Internal Server Error` — unexpected server error

---

## Validation

Use Bean Validation for request validation.

Prefer:

```java
@NotBlank
@Size
@Email
@NotNull
@Positive
```

Example:

```java
public record CreateUserRequest(
    @NotBlank String name,
    @Email @NotBlank String email
) {
}
```

Do not duplicate simple validation logic in controllers when Bean Validation can handle it.

---

## Exception Handling

Use centralized exception handling with `@RestControllerAdvice`.

Prefer:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
}
```

Do not use scattered `try/catch` blocks in controllers for normal application exceptions.

Create meaningful domain/application exceptions when appropriate.

Example:

```java
public class UserNotFoundException extends RuntimeException {
}
```

Do not expose stack traces or internal exception messages to API clients.

---

## Transaction Rules

Use `@Transactional` primarily at the service layer.

Example:

```java
@Transactional
public void updateUser(...) {
    ...
}
```

Use:

```java
@Transactional(readOnly = true)
```

for read-only service operations when appropriate.

Avoid placing transaction boundaries in controllers.

---

## JPA / Entity Rules

- Prefer `LAZY` relationships unless eager loading is explicitly required.
- Avoid unnecessary bidirectional relationships.
- Be careful with `@OneToMany`.
- Do not expose entities directly in REST responses.
- Avoid `CascadeType.ALL` unless there is a clear aggregate ownership relationship.
- Do not use entity setters indiscriminately.
- Prefer meaningful domain methods when modifying important state.

Example:

```java
public void changeEmail(String email) {
    this.email = email;
}
```

instead of exposing unrestricted setters where possible.

---

## Repository Rules

Use Spring Data JPA repositories where appropriate.

Prefer derived queries for simple queries:

```java
Optional<User> findByEmail(String email);
```

Use `@Query` for more complex queries.

For performance-sensitive queries, consider:

- fetch joins
- projections
- entity graphs
- pagination

Avoid introducing native SQL unless it provides a clear benefit.

---

## N+1 Query Prevention

Be alert for N+1 query problems.

When loading relationships for API responses, consider:

- `JOIN FETCH`
- `@EntityGraph`
- DTO projections

Do not solve N+1 problems by globally changing relationships to `EAGER`.

---

## Pagination

For potentially large collections, use pagination.

Prefer:

```java
Page<User> findAll(Pageable pageable);
```

rather than loading the entire dataset.

Do not return unbounded database collections from APIs unless the dataset is guaranteed to remain small.

---

## Database Changes

Never modify production database schema implicitly.

If the project uses Flyway or Liquibase:

- Add a migration for schema changes.
- Follow the existing migration naming convention.
- Never edit an already-applied migration.
- Make migrations backward-compatible when possible.

Before changing entities, check whether a corresponding database migration is required.

---

## Testing

Every meaningful behavior change should have appropriate tests.

Prefer:

### Unit tests

For:

- domain logic
- service logic
- validation-related business rules

### Integration tests

For:

- repository behavior
- database interactions
- Spring configuration
- important application workflows

### Controller/API tests

For:

- HTTP status codes
- request validation
- response structure
- authentication/authorization behavior

Use JUnit 5.

Use Mockito only when mocking provides a meaningful isolation boundary.

Prefer Testcontainers for integration tests involving MySQL or other infrastructure dependencies. `./gradlew test` requires Docker to be running.

Most controller tests share one MySQL instance via Testcontainers and are not wrapped in `@Transactional`, so state persists across test methods in the same class — make setup idempotent (e.g. `findByX(...).orElseGet(() -> repository.save(...))`) rather than assuming a clean table per test.

### Frontend

There is no automated frontend test suite (deliberate scope decision, given the time/browser-count tradeoff). For any frontend change:

- Run `npm run build` (type-checks via `tsc -b`, then builds).
- Manually verify in a browser against a real backend + MySQL instance, exercising the actual golden path (see `frontend/README.md` and `docs/API.md` for the relevant endpoints/`allowedActions`).

---

## Test Naming

Test names should clearly describe behavior.

Prefer:

```java
@Test
void shouldThrowExceptionWhenUserDoesNotExist() {
}
```

or, when appropriate:

```java
@Test
void 존재하지_않는_사용자를_조회하면_예외가_발생한다() {
}
```

Tests should verify behavior rather than implementation details.

---

## Security

Never hard-code:

- passwords
- JWT secrets
- API keys
- database credentials
- private keys

Use environment variables or the project's existing secret-management mechanism.

Do not weaken authentication or authorization merely to make tests pass.

Do not disable CSRF, CORS, authentication, or authorization globally without understanding the project's security requirements.

---

## Logging

Use the project's existing logging framework.

Prefer parameterized logging:

```java
log.info("User created: id={}", userId);
```

Do not log:

- passwords
- access tokens
- refresh tokens
- API keys
- sensitive personal information

Avoid excessive debug logging in production code.

---

## Configuration

Keep environment-specific configuration separate.

Prefer:

```
application.yml
application-local.yml
application-test.yml
application-prod.yml
```

Do not commit environment-specific secrets.

Do not change production configuration unless explicitly requested.

---

## Dependencies

Before adding a dependency:

1. Check whether the functionality already exists in the project.
2. Check whether Spring Boot or an existing dependency already provides it.
3. Prefer well-maintained and widely adopted libraries.
4. Keep the dependency scope as narrow as possible.

Do not add libraries merely to avoid writing a small amount of straightforward code.

---

## Code Style

Follow the existing formatting and naming conventions.

Use:

- `camelCase` for variables and methods
- `PascalCase` for classes
- descriptive names
- small, focused methods

Avoid:

- unnecessary abbreviations
- deeply nested conditionals
- overly clever streams
- premature abstractions
- duplicated business logic

Prefer early returns when they improve readability.

---

## Lombok

If Lombok is already used, follow the existing project conventions.

Avoid blindly adding:

```java
@Data
```

to JPA entities.

Prefer explicitly choosing the required Lombok annotations.

Be especially careful with generated:

- `equals`
- `hashCode`
- `toString`

on JPA entities.

---

## API Documentation

This project has two, separate layers — keep both accurate when endpoints change:

- **`docs/API.md`** is the hand-written, authoritative contract (written before/alongside implementation, per stage). Update it whenever an endpoint, request/response field, or `allowedActions` value changes, and mark anything not actually implemented as **미구현** rather than leaving it looking real (see Project-Specific Gotchas).
- **springdoc-openapi** (Swagger UI / OpenAPI, already a dependency) generates a live spec from the controllers automatically at `/swagger-ui.html` / `/v3/api-docs` — no manual annotation work is required for it to stay accurate, but it only reflects what's implemented, not the design intent `docs/API.md` also captures (open questions, deferred features, rationale).

---

## Project-Specific Gotchas

- Jackson 3 in this Spring Boot version uses the `tools.jackson.*` package, not `com.fasterxml.jackson.*`.
- `MockMvcBuilderCustomizer` lives at `org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer` in this Spring Boot version, not the older `org.springframework.boot.test.autoconfigure.web.servlet` path.
- Most `@AutoConfigureMockMvc` controller tests get a default admin `Authorization` header injected automatically (see `TestSecurityHeaderConfig` / `TestAdminTokenProvider`) so they don't need to log in explicitly. A test that must authenticate as a specific non-default user should build its own `MockMvc` with `SecurityMockMvcConfigurers.springSecurity()` instead (see `AuthenticationTest`, `WorkerStationTest`).
- Access tokens are self-signed HMAC-SHA256, not JWTs from a library — see `security/AccessTokenService`. Don't introduce a JWT dependency for this.
- The frontend talks to the backend through the Vite dev proxy (`frontend/vite.config.ts`, `/api` → `localhost:8080`), not backend CORS config — prefer extending the proxy setup over adding CORS headers for local dev needs.
- Task/robot/alarm responses include a backend-computed `allowedActions` array; the frontend renders buttons purely from that array rather than re-deriving allowed transitions client-side. Keep new actions consistent with this pattern.
- `docs/API.md`, `docs/ERD.md`, and `docs/STATE_MACHINES.md` are design drafts (`v0`) written before/alongside implementation, so some documented endpoints/tables were never built (e.g. `loads`/`load_transfer_events`, `inbox_messages`, `audit_logs`) — these are explicitly marked **미구현** where they appear. Check for that marker before assuming a documented endpoint or table actually exists in code; grep the controller/entity/migration directly to confirm. (Alarms — stage 14 — and the SSE event stream — stage 15, Redis-backed — are implemented and no longer in this unimplemented list.)
- This Spring Boot version ships **no Kafka autoconfiguration** anywhere (`spring-kafka`'s jar and `spring-boot-autoconfigure` both lack it, unlike Redis/JPA which get a dedicated `spring-boot-data-redis`-style module) — `KafkaTemplate`/`KafkaAdmin`/`ProducerFactory` beans must be declared by hand (see `config/KafkaConfig`). Injecting `KafkaTemplate<String, String>` only works because that config declares it with that exact generic type — do not rely on autoconfiguration existing for any future Kafka work.
- The Testcontainers Kafka module's Gradle coordinate is `org.testcontainers:testcontainers-kafka` (not `org.testcontainers:kafka`, which is stuck on the old 1.x line and conflicts with this project's testcontainers 2.x). Its `KafkaContainer` also isn't recognized by `@ServiceConnection` here — register `spring.kafka.bootstrap-servers` manually via `@DynamicPropertySource` instead (see `support/AbstractIntegrationTest`).
- `application-local.yaml` has exactly one top-level `app:` key — YAML silently fails Spring Boot startup ("found duplicate key app") if a second one is added. When adding a new `app.*` property block, merge it into the existing `app:` section rather than appending a new one; this broke `bootRun` once during stage 18 and only surfaced via manual verification, not `./gradlew test` (the `test` profile uses a different file).
- `MqttRobotGateway`'s `RobotGateway.dispatch()` is synchronous with a **hardcoded 3-second wait** for a matching MQTT `state` message before giving up (`UNKNOWN`) — a deliberate, documented simplification (see `docs/API.md` §7) to avoid refactoring `TaskService`'s synchronous command-completion contract. Don't "fix" this into a blocking wait removal or a longer timeout without re-reading that tradeoff; a real robot integration would need an actual async redesign instead.
- Two `RobotGateway` beans exist (`SimulatorRobotGateway` always, `MqttRobotGateway` only when `app.robot-gateway.mqtt.enabled=true`) — disambiguated via `@Primary` on the MQTT one, not `@ConditionalOnProperty` on the simulator. `RobotStateService` injects the concrete `SimulatorRobotGateway` type directly (for fault-injection tooling), so that bean must stay unconditionally registered — don't make it conditional.
- `TokenAuthenticationFilter` accepts an `access_token` query-param token **only** for `/api/events/stream` (browsers' native `EventSource` can't set custom headers) — every other endpoint still requires the `Authorization` header. Don't widen this fallback to other paths without re-reading the tradeoff (token exposed in URL) documented in `docs/API.md` §5.
- Stage 26 security: `TokenAuthenticationFilter` re-loads the token's user on **every request** (`UserRepository.findWithRoleById`, a `join fetch` because the filter runs outside any transaction and `User.role` is LAZY) and grants authorities from the DB role, not the token's `role` claim — a `DISABLED` user's still-unexpired token gets 401. It skips `/api/external/**` entirely: that path authenticates only via `ExternalApiKeyFilter` (`X-API-Key` vs `app.external.api-key`, fail-closed when blank), so the default admin Bearer header that `@AutoConfigureMockMvc` tests carry does **not** authorize external calls — external API tests must send `X-API-Key: test-only-external-api-key` (see `ExternalTaskIntegrationTest`).
- Stage 26: every `/api/worker/**` controller method calls `WorkerAccessService.check*Access(username, ...)` first (403 `STATION_ACCESS_DENIED` unless ADMIN or assigned via `user_station_assignments`; tasks allow origin **or** destination station). Any new worker endpoint must do the same — role checks in `SecurityConfig` alone are not enough.
- `OutboxPublisher.publishPending()` wraps each event's publish attempt in its own try/catch — found via manual testing (stage 19) that without it, one event failing (e.g. Kafka unreachable) threw out of the loop, permanently blocking every later PENDING event behind it (head-of-line blocking) since the same failing event kept getting re-fetched every scheduler tick. Keep the per-event isolation if you touch this method.
- Stage 27: `OutboxPublisher` must `.get(timeout)` the future returned by `KafkaTemplate.send()` — it's async and never throws on broker failure, so skipping the wait silently marks undelivered events `PUBLISHED`. Order is Kafka first, then Redis, so a Kafka-failed event isn't pushed to SSE clients and then re-pushed on retry. Unit tests mocking `KafkaTemplate` must return a `CompletableFuture` (a `null` return now NPEs inside the try and leaves the event PENDING).
- Stage 27: `EventStreamController` registers the Redis listener **before** querying the `Last-Event-ID` backlog, and `SseEventRelay` buffers live events during replay and drops ones already sent from the backlog (by id set, not `id <= lastSent` — Redis delivery order isn't guaranteed to match id order). Don't move the subscribe back after the backlog query; that reintroduces the gap where events published in between were lost.
- Stage 27: every transition into `RECOVERY_REQUIRED` in `TaskService` goes through the private `markRecoveryRequired(...)` helper, which also raises `TASK_RECOVERY_REQUIRED`; `recover` auto-resolves it (RESUME resolves before re-dispatching). Add any new path into that status through the helper, not `task.markRecoveryRequired()` directly.
- Stage 29: `AutoDispatchScheduler` and `RobotConnectionScheduler` beans are always registered but their `@Scheduled` methods no-op unless `app.dispatch.auto.enabled` / `app.robot.connection-monitor.enabled` is true (only `application-local.yaml` turns them on). Keep them off in tests — an auto-dispatch tick would assign QUEUED tasks that other tests assert on. Tests call `dispatchActiveSites()` / `RobotConnectionService` methods directly instead.
- Stage 29: `RobotConnectionService` updates `robot_current_states` only through the conditional bulk `@Modifying` queries in `RobotCurrentStateRepository` (not load-modify-save), because the gateways' `report()` may be updating the same row concurrently and a full-row dirty-check update would clobber it. `MqttRobotGateway` records telemetry on a separate single-thread executor, never the Paho callback thread — blocking that thread on the row lock held by an in-flight `dispatch()` would stop it from completing the order future. Tests that fake a stale heartbeat must shift `received_at` in SQL (`date_sub(received_at, ...)`), not bind a Java `Timestamp` (JVM timezone ≠ the UTC Hibernate stores).

---

## Before Modifying Code

Before making a change:

1. Inspect the relevant package structure.
2. Read the existing implementation.
3. Identify related tests.
4. Check interfaces and callers.
5. Check database/entity implications.
6. Check API compatibility.
7. Make the smallest reasonable change.

Do not rewrite unrelated code.

---

## After Modifying Code

Run the narrowest relevant tests first.

For example:

```bash
./gradlew test
```

If appropriate, also run:

```bash
./gradlew build
```

Before finishing:

1. Review the diff.
2. Check for accidental changes.
3. Check for unused imports.
4. Check for debug code.
5. Check for secrets.
6. Confirm tests pass.
7. Summarize what changed and why.

---

## When Requirements Are Ambiguous

Do not invent important business requirements.

If ambiguity affects:

- data integrity
- security
- API compatibility
- database schema
- financial behavior
- destructive operations

ask for clarification before making the change.

For low-risk ambiguity, follow existing project conventions and choose the simplest consistent implementation.

---

## Git

Do not:

- reset unrelated user changes
- overwrite uncommitted work
- force-push
- rewrite history

unless explicitly requested.

Keep changes focused and easy to review.

Staged feature work (one implementation step from `docs/DESIGN.md` §11) follows: branch `stage-N-<short-description>` off `main` → implement → test/build → commit → push → open a PR via `gh pr create` → merge via `gh pr merge --merge`. Small, non-staged changes (docs, fixes) can go straight to `main` if the user asks for it.

---

## Definition of Done

A change is considered complete when:

- The requested behavior is implemented.
- Existing behavior is preserved unless intentionally changed.
- Appropriate tests are added or updated.
- Relevant tests pass.
- No secrets are introduced.
- No unrelated files are modified.
- API and database implications have been considered.
- The final diff is clean and understandable.
