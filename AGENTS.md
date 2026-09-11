# AGENTS.md

## Project Overview

This is a Spring Boot backend application.

### Tech Stack

- Java 21
- Spring Boot 3.x
- Gradle
- Spring MVC
- Spring Data JPA
- MySQL
- JUnit 5
- Mockito
- Testcontainers

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

Prefer the following layered structure unless the existing project uses a different established structure:

```
src/main/java/
└── com.example.project/
    ├── controller/
    ├── service/
    ├── repository/
    ├── domain/
    │   └── entity/
    ├── dto/
    │   ├── request/
    │   └── response/
    ├── exception/
    └── config/
```

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

Prefer Testcontainers for integration tests involving PostgreSQL or other infrastructure dependencies.

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

If the project uses OpenAPI/Swagger:

- Update API documentation when endpoints change.
- Keep request/response schemas accurate.
- Document important error responses.

Do not introduce Swagger/OpenAPI solely for a small change if the project does not already use it.

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
