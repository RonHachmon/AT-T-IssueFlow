# Implementation Plan: Users CRUD

**Branch**: `002-users-crud` | **Date**: 2026-05-21 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/002-users-crud/spec.md`

## Summary

Add a `User` entity (Long id, BCrypt-ready `passwordHash` column) and the
five REST endpoints from the spec: `POST /users`, `GET /users/{userId}`,
`GET /users` (paginated), `PATCH /users/{userId}`, `DELETE /users/{userId}`.
Schema is shipped via a Flyway `V2__users.sql` migration. Validation lives
on DTOs via jakarta.validation; the existing `GlobalExceptionHandler` maps
`MethodArgumentNotValidException` to 422 ProblemDetail with field-level
errors automatically. Duplicate username/email is detected up-front in the
service via `existsByUsernameIgnoreCase` / `existsByEmailIgnoreCase` and
mapped to a 409 — case-insensitive uniqueness is enforced at the database
by functional unique indexes on `LOWER(username)` / `LOWER(email)`.
Entity↔DTO mapping is generated at compile time by MapStruct.

## Technical Context

**Language/Version**: Java 21 (unchanged from skeleton)
**Primary Dependencies (new)**:
- `org.mapstruct:mapstruct` and `org.mapstruct:mapstruct-processor` for
  compile-time entity↔DTO mapping
- `org.springframework.security:spring-security-crypto` for
  `BCryptPasswordEncoder` (the *only* Spring Security module we pull in
  this phase — no autoconfiguration, no filter chain)
- Existing: Spring Data JPA, Spring Boot Validation, Flyway, Lombok,
  PostgreSQL driver
**Storage**: PostgreSQL 16 via the existing `compose.yml`. New table
`users` introduced by Flyway migration `V2__users.sql`.
**Testing** (per constitution Principle II):
- **Pure-JVM unit tests**: `UserServiceTest` (Mockito mocks
  `UserRepository`, `UserMapper`, `BCryptPasswordEncoder`) covering
  duplicate-detection, role-enum validation, immutability of
  username/email on update, and not-found mapping.
- **Pure-JVM unit tests**: `UserControllerTest` (Mockito mocks
  `UserService`) covering status-code mapping per endpoint.
- **Postman**: full collection covering all 5 endpoints + happy and
  validation-error paths.
- The single allowed Spring-context smoke test (`ApplicationStartupTest`)
  already exists; no new Spring-context tests required for this feature
  (validation-trigger behavior is covered by Postman).
**Target Platform**: same as skeleton — locally runnable JVM + Postgres.
**Project Type**: web service (unchanged).
**Performance Goals**: list endpoint serves first page (≤20 records) in
under 200 ms locally; create/update/delete complete in under 100 ms.
**Constraints**:
- `passwordHash` column is **nullable** in this feature (no login yet).
  The column exists so the `users` table is forward-compatible with the
  Phase-2 auth feature — no schema migration at that point.
- The API surface in this feature MUST NOT accept, expose, or echo a
  password. `BCryptPasswordEncoder` is wired up as a bean but unused at
  the boundary.
- Username and email are **immutable** after creation (spec FR-011).
**Scale/Scope**: 1 entity, 5 endpoints, ~12 Java classes/records, 1
migration, 1 unique-index pair.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Check each gate against `.specify/memory/constitution.md` (v1.0.0). For any
"No", either fix the design or record a justification in **Complexity
Tracking** below.

| # | Gate | Pass? | Notes |
|---|------|-------|-------|
| I  | **Clean Code** — function names reveal intent; no functions mix abstraction levels; no flag arguments; no magic numbers/strings. | [x] | Service methods (`create`, `getById`, `list`, `update`, `delete`) are single-responsibility. Status codes and constraint names extracted to constants. No flag arguments planned — partial update is naturally expressed through `UpdateUserRequest` field-presence. |
| II | **Testing Standards** — JUnit 5 + Mockito unit tests (no Spring) AND Postman; complex tests required where branching/state/edge cases exist. | [x] | `UserServiceTest` covers duplicate detection, immutability rules, not-found mapping, role validation. `UserControllerTest` covers status-code mapping per endpoint. Postman collection covers all 5 endpoints + 422/409/404 error paths. No trivial getter/mapper tests planned. |
| III| **Documentation Discipline** — Javadoc on every new public method; README endpoint rows updated; `run.md` updated only if startup steps changed; `prompts.md` records AI use. | [x] | Public methods on `UserService`, `UserController`, `UserMapper`, `BCryptPasswordEncoder` provider all get Javadoc. README's Users section will be rewritten (the existing rows use non-REST verbs/status codes — fixing them as part of this PR). `run.md` unchanged (no startup-step changes). `prompts.md` gains a "Feature 002 — Users CRUD" entry. |
| IV | **API Consistency** — correct verbs/status codes (201/204/404/409/422); plural noun paths; RFC 7807 errors; camelCase JSON / snake_case SQL; `<entity>Id` foreign keys; DTOs (never entities) at boundary; canonical pagination envelope; popular libraries over hand-rolled. | [x] | `POST /users` → 201 + `Location`. `DELETE /users/{userId}` → 204. `GET /users/{userId}` → 404 on miss. Duplicate username/email → 409. Bean Validation failure → 422 with `errors[]` extension (already implemented in `GlobalExceptionHandler`). Plural `/users`. camelCase DTOs (`fullName`, `createdAt`); snake_case SQL (`full_name`, `created_at`). DTOs never expose `User` entity. Pagination envelope `{ data, page, pageSize, total }` defined in `contracts/pagination.md` — first feature to use it. Reuses MapStruct (popular library), Bean Validation (popular library), Spring Data JPA derived queries — no hand-rolled SQL. |

Phase 0/1 gate: **PASS**.

Post-design gate (re-evaluated after Phase 1 artifacts): **PASS** — no
violations introduced.

## Project Structure

### Documentation (this feature)

```text
specs/002-users-crud/
├── plan.md                # This file
├── spec.md                # Feature specification
├── research.md            # Phase 0 output
├── data-model.md          # Phase 1 output (User entity + users table)
├── quickstart.md          # Phase 1 output (verify the feature end-to-end)
├── contracts/
│   ├── users.openapi.yaml      # All 5 user endpoints
│   └── pagination.md           # Canonical pagination envelope (project-wide)
└── checklists/
    └── requirements.md    # Spec-quality checklist (already passing)
```

### Source Code (repository root)

```text
issueflow-java/
├── pom.xml                                    # +mapstruct, +mapstruct-processor, +spring-security-crypto
└── src/
    ├── main/
    │   ├── java/com/att/tdp/issueflow/
    │   │   ├── common/
    │   │   │   ├── error/
    │   │   │   │   ├── DuplicateResourceException.java   # NEW — domain ex for 409
    │   │   │   │   ├── ErrorType.java                    # +DUPLICATE_RESOURCE constant
    │   │   │   │   ├── GlobalExceptionHandler.java       # +handler for DuplicateResourceException
    │   │   │   │   ├── NotFoundException.java            # NEW — domain ex for 404 from services
    │   │   │   │   ├── ProblemDetailFactory.java         # (existing, unchanged)
    │   │   │   ├── pagination/
    │   │   │   │   ├── PagedResponse.java                # NEW — canonical envelope record<T>
    │   │   │   │   └── package-info.java
    │   │   │   ├── security/
    │   │   │   │   └── PasswordEncoderConfiguration.java # NEW — BCryptPasswordEncoder @Bean
    │   │   │   └── (existing health/, ClockConfiguration.java)
    │   │   └── user/
    │   │       ├── User.java                              # @Entity
    │   │       ├── UserRepository.java                    # JpaRepository<User, Long>
    │   │       ├── UserService.java                       # business logic
    │   │       ├── UserController.java                    # @RestController
    │   │       ├── UserMapper.java                        # MapStruct interface
    │   │       ├── Role.java                              # enum
    │   │       ├── dto/
    │   │       │   ├── CreateUserRequest.java
    │   │       │   ├── UpdateUserRequest.java
    │   │       │   └── UserResponse.java
    │   │       └── (existing package-info.java is replaced)
    │   └── resources/
    │       └── db/migration/
    │           └── V2__users.sql                          # NEW — users table + functional unique indexes
    └── test/
        └── java/com/att/tdp/issueflow/user/
            ├── UserServiceTest.java                       # pure JVM + Mockito
            └── UserControllerTest.java                    # pure JVM + Mockito
```

**Structure Decision**:

- All user-feature code lives under `com.att.tdp.issueflow.user` — the
  slice claimed by the package-by-feature decision in feature 001.
- DTOs go in `user/dto/` to keep the slice root focused on the four
  classes a reviewer typically wants first (`User`, `UserRepository`,
  `UserService`, `UserController`).
- Cross-cutting new pieces (the `PagedResponse` envelope, the
  `BCryptPasswordEncoder` bean, the new domain exceptions) go under
  `common/` so the next feature (tickets) inherits them.
- `Role` is a top-level enum in `user/` — moving it later if a different
  slice needs it is trivial.
- Test mirroring is preserved: each production class with non-trivial
  logic gets a sibling test class. `UserMapper` (MapStruct-generated)
  and `User` (data carrier) are excluded — testing generated code or
  field accessors adds maintenance cost without signal (Principle II).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations. Table intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
