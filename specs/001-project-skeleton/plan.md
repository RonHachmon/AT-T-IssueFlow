# Implementation Plan: Project Skeleton

**Branch**: `001-project-skeleton` | **Date**: 2026-05-20 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-project-skeleton/spec.md`

## Summary

Bring up the empty house: a Spring Boot 3.4 application on Java 21, talking to a
PostgreSQL database started from the provided `compose.yml`, with schema
managed by Flyway (no `ddl-auto=update`), an RFC 7807 `@RestControllerAdvice`
scaffold ready for future endpoints to extend, a single `GET /health` endpoint
that confirms application + database liveness, and a package-by-feature source
layout (`user`, `project`, `ticket`, `comment`, `auth`, `common`) so future
features each land in their own slice.

## Technical Context

**Language/Version**: Java 21 (LTS, set in `pom.xml`'s `java.version` property)
**Primary Dependencies**: Spring Boot 3.4.2 (`spring-boot-starter-web`,
`spring-boot-starter-data-jpa`, `spring-boot-starter-validation`,
`spring-boot-starter-actuator`), PostgreSQL JDBC driver, Flyway
(`flyway-core` + `flyway-database-postgresql`), Lombok,
`spring-boot-starter-test` (JUnit 5 + Mockito + AssertJ, transitive)
**Storage**: PostgreSQL 16 (latest official `postgres` image as configured in
`compose.yml`), connection-pooled by HikariCP (Spring Boot default). H2
remains in `pom.xml` for legacy reasons but is **not** used by this skeleton;
removal deferred to a later cleanup pass.
**Testing**: JUnit 5 + Mockito for unit tests with **no Spring context**
(constitution Principle II). One Spring-context smoke test (`@SpringBootTest`)
to prove the application starts and the health endpoint responds. Postman
collection in `postman/` exercises `GET /health` end-to-end via Newman.
**Target Platform**: Locally runnable JVM on Linux / macOS / Windows; PostgreSQL
via Docker Compose. Production deployment topology out of scope (per spec).
**Project Type**: Web service (single Spring Boot application). Single-module
Maven project rooted at `pom.xml`.
**Performance Goals**: Application reaches "ready" within 30 seconds of the
database accepting connections (spec SC-003). Health endpoint responds in
under 100 ms in the steady state.
**Constraints**:
- Schema is managed by Flyway only — `spring.jpa.hibernate.ddl-auto=none` (or
  `validate` once entities exist). `update` is forbidden by Principle IV
  reasoning (review/version control of schema changes).
- Errors at all controller boundaries (current and future) return
  `application/problem+json` per RFC 7807, via a single
  `@RestControllerAdvice`.
- Configuration secrets (DB URL, username, password) come from environment
  variables with safe local defaults in `application.yml` — no production
  credentials in source.
**Scale/Scope**: Skeleton has one endpoint (`GET /health`). No business
entities yet; future features will add up to ~10 entities and ~30 endpoints
(per README), but those are sized by future features, not this one.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Check each gate against `.specify/memory/constitution.md` (v1.0.0). For any
"No", either fix the design or record a justification in **Complexity
Tracking** below.

| # | Gate | Pass? | Notes |
|---|------|-------|-------|
| I  | **Clean Code** — function names reveal intent; no functions mix abstraction levels; no flag arguments; no magic numbers/strings in planned code. | [x] | Planned classes (`HealthController`, `GlobalExceptionHandler`, `ProblemDetailFactory`) each have a single responsibility. Status codes and problem `type` URIs extracted as constants. No flag arguments in any planned signature. |
| II | **Testing Standards** — plan lists JUnit 5 + Mockito unit tests (no Spring context) AND Postman tests for every endpoint; complex tests are planned wherever branching, state transitions, concurrency, or edge cases exist. | [x] | Unit: `GlobalExceptionHandlerTest` (pure JVM, Mockito for any collaborators) covering exception → ProblemDetail mapping branches; `HealthControllerTest` for the controller's mapping logic. Spring-context smoke: `ApplicationStartupTest` proves the context loads and `/health` returns 200. Postman: `postman/issueflow.postman_collection.json` with two requests (healthy + DB-down). |
| III| **Documentation Discipline** — plan calls out Javadoc on every new public method, README endpoint table updates, and (if startup changes) `run.md` updates. AI-assisted changes will record a `prompts.md` entry. | [x] | Every public method on `HealthController`, `GlobalExceptionHandler`, and `ProblemDetailFactory` gets Javadoc (purpose, params, return, exceptions). README gains a "Health" endpoint row in the API table. `run.md` is created (it does not yet exist) and validated from a fresh clone. `prompts.md` entry recorded for this feature. |
| IV | **API Consistency** — new endpoints use correct verbs and status codes (201/204/404/409/422), plural-noun paths, RFC 7807 errors, camelCase JSON / snake_case SQL, `<entity>Id` foreign-key naming, DTOs (never entities) at the controller boundary, and the canonical pagination envelope. Preferred libraries (Bean Validation, MapStruct, springdoc-openapi, Problem Details) are reused rather than re-invented. | [x] | `GET /health` → 200 healthy / 503 unhealthy, both `application/json`. The endpoint is operational and singular (`/health`), not a business resource collection, so the "plural noun" rule does not apply (documented). Errors at every other path (including 404s for unknown routes) flow through the `@RestControllerAdvice` and emit RFC 7807 `application/problem+json` using Spring's built-in `ProblemDetail`. No entities at the boundary — `HealthResponse` is a record. |

Phase 0/1 gate: **PASS**.

Post-design gate (re-evaluated after Phase 1 artifacts): **PASS** — no
violations introduced by the contracts or data model.

## Project Structure

### Documentation (this feature)

```text
specs/001-project-skeleton/
├── plan.md                # This file
├── spec.md                # Feature specification
├── research.md            # Phase 0 output
├── data-model.md          # Phase 1 output (operational components only)
├── quickstart.md          # Phase 1 output (verify the skeleton)
├── contracts/
│   ├── health.openapi.yaml      # GET /health contract
│   └── problem-details.md       # RFC 7807 error envelope reference
└── checklists/
    └── requirements.md    # Spec-quality checklist (already passing)
```

### Source Code (repository root)

```text
issueflow-java/
├── pom.xml                # Maven build (add flyway-core, flyway-database-postgresql, actuator)
├── compose.yml            # Postgres + Adminer (already provided)
├── mvnw / mvnw.cmd        # Maven wrapper
├── README.md              # API surface; gains a "Health" row
├── run.md                 # Created by this feature (fresh-clone runbook)
├── prompts.md             # Created by this feature (AI usage log)
├── postman/
│   └── issueflow.postman_collection.json   # Newman-runnable; covers /health
└── src/
    ├── main/
    │   ├── java/com/att/tdp/issueflow/
    │   │   ├── IssueflowApplication.java   # @SpringBootApplication (already exists)
    │   │   ├── common/
    │   │   │   ├── package-info.java
    │   │   │   ├── error/
    │   │   │   │   ├── GlobalExceptionHandler.java   # @RestControllerAdvice, RFC 7807
    │   │   │   │   ├── ProblemDetailFactory.java     # Maps exceptions → ProblemDetail
    │   │   │   │   └── ErrorType.java                # Stable error `type` URI constants
    │   │   │   └── health/
    │   │   │       ├── HealthController.java         # GET /health
    │   │   │       └── HealthResponse.java           # DTO record
    │   │   ├── user/package-info.java                # Placeholder slice
    │   │   ├── project/package-info.java             # Placeholder slice
    │   │   ├── ticket/package-info.java              # Placeholder slice
    │   │   ├── comment/package-info.java             # Placeholder slice
    │   │   └── auth/package-info.java                # Placeholder slice
    │   └── resources/
    │       ├── application.yaml                      # Postgres datasource + Flyway + ddl-auto=none
    │       └── db/migration/
    │           └── V1__baseline.sql                  # Empty baseline (no schema yet)
    └── test/
        ├── java/com/att/tdp/issueflow/
        │   ├── ApplicationStartupTest.java           # @SpringBootApplication smoke test
        │   ├── common/
        │   │   ├── error/
        │   │   │   └── GlobalExceptionHandlerTest.java   # Pure JVM + Mockito
        │   │   └── health/
        │   │       └── HealthControllerTest.java         # Pure JVM, no Spring
        └── resources/
            └── application.yaml                      # H2 in-memory for the startup test
```

**Structure Decision**:

- **Package-by-feature** under `com.att.tdp.issueflow.<slice>` per user input.
  Six slices are reserved up front (`user`, `project`, `ticket`, `comment`,
  `auth`, `common`); five are scaffolded with `package-info.java` so the
  intended structure is visible from day one without empty `.java` stubs
  cluttering source folders.
- `common` is the only slice that ships code in this feature: cross-cutting
  error handling and the health endpoint. The health endpoint lives in
  `common.health` because it is plumbing, not a business resource.
- Tests mirror the package structure under `src/test/java` so each unit test
  sits next to its target class.
- Flyway migrations live at the conventional `src/main/resources/db/migration`
  path; `V1__baseline.sql` is an empty file (just a SQL comment) so Flyway
  has at least one migration to claim during startup.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations. Table intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
