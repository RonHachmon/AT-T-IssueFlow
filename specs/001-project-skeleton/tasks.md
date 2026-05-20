---
description: "Task list for project-skeleton feature"
---

# Tasks: Project Skeleton

**Input**: Design documents from `specs/001-project-skeleton/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: **MANDATORY** for this project. Constitution Principle II requires both
JUnit 5 + Mockito unit tests (no Spring context, except one narrowly-scoped
smoke test) **and** Postman API tests for every endpoint. Sanity tests are
non-negotiable; complex tests are required wherever branching, state
transitions, or edge cases exist (e.g., the exception handler's per-exception
branches).

**Organization**: Tasks are grouped by user story (US1, US2, US3 from
[spec.md](spec.md)). Each user story phase, once Setup + Foundational are
done, is independently completable and independently testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable — different files, no dependency on an
  incomplete task
- **[Story]**: which user story this task belongs to (US1, US2, US3); omitted
  for Setup / Foundational / Polish

## Path Conventions

Single-module Spring Boot project. All paths are relative to the repository
root (`c:\Users\ronha\OneDrive\Desktop\visual studio\issueflow-java\`):

- Application code: `src/main/java/com/att/tdp/issueflow/<slice>/`
- Test code: `src/test/java/com/att/tdp/issueflow/<slice>/`
- Configuration: `src/main/resources/`, `src/test/resources/`
- Postman collection: `postman/issueflow.postman_collection.json`
- Docs at repo root: `README.md`, `run.md`, `prompts.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Bring `pom.xml` to the dependency set the plan demands and lay
out the package-by-feature skeleton so every later task has a clear home.

- [x] T001 Update [pom.xml](../../pom.xml) — added actuator, flyway-core, flyway-database-postgresql. Java 21 retained. (Note: `h2` is in `test` scope now, not `runtime` — that's the right scope for this skeleton.)
- [x] T002 [P] Created six `package-info.java` files (user, project, ticket, comment, auth, common).
- [x] T003 [P] `postman/` already exists with a placeholder collection — T018 will update the Health folder in place.

**Checkpoint**: Project compiles (`./mvnw clean compile` exits 0) with the new dependencies wired in.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Replace the template-residue `application.yaml`, wire Flyway,
and ship the RFC 7807 `@RestControllerAdvice` scaffold. **No user story
work can begin until this phase is complete.**

- [x] T004 Rewrote main `application.yaml` — env-overridable Postgres datasource, `ddl-auto: none`, explicit PostgreSQL dialect, Flyway enabled, Actuator endpoints excluded from HTTP (HealthEndpoint bean still available for injection).
- [x] T005 [P] Rewrote test `application.yaml` — H2 with `MODE=PostgreSQL`, Flyway disabled, `create-drop`, removed `mssql` artifact.
- [x] T006 [P] `src/main/resources/db/migration/V1__baseline.sql` created with the placeholder comment.
- [x] T007 `common/error/ErrorType.java` — 5 stable problem-type URI constants, Javadoc on each.
- [x] T008 [P] `common/error/ProblemDetailFactory.java` — 5 typed factory methods (`notFound`, `validationFailed`, `malformedRequest`, `conflict`, `internalError`), Javadoc on every public method, no flag arguments.
- [x] T009 `common/error/GlobalExceptionHandler.java` — `@RestControllerAdvice` with handlers for `MethodArgumentNotValidException` (422 + errors array), `HttpMessageNotReadableException` (400), `NoResourceFoundException` (404), and catch-all `Exception` (500, logs server-side).
- [x] T010 [P] `common/error/GlobalExceptionHandlerTest.java` — 4 tests, pure JVM + Mockito (no Spring), all green. Sentence-named, AAA-structured. **`./mvnw test -Dtest=GlobalExceptionHandlerTest` reports BUILD SUCCESS.**

**Checkpoint**: Foundation ready. `./mvnw test` runs `GlobalExceptionHandlerTest` and it passes. User story implementation can now begin.

---

## Phase 3: User Story 1 — Fresh Clone Builds Successfully (Priority: P1) 🎯 MVP

**Goal**: A developer who has never seen this repository can clone it, follow `run.md`, and end up with a green build on the first attempt.

**Independent Test**: On a clean machine with Java 21 + Docker installed: `git clone … && cd issueflow-java && ./mvnw clean verify` exits zero, and `./mvnw test` shows all unit tests passing.

- [x] T011 [US1] `run.md` written at repo root — 8-step runbook covering prereqs, db, build, run, health-check (healthy + unhealthy), tests, Postman, env-var overrides, troubleshooting.
- [x] T012 [P] [US1] `README.md` Health API section inserted before Users APIs with full request/response shape and a link to the OpenAPI contract.
- [x] T013 [P] [US1] `prompts.md` created — names Claude Opus 4.7 and records the four `/speckit-*` prompts verbatim plus implementation-time corrections (Spring Boot class name, Spotless plugin, pre-existing Postman placeholders).

**Checkpoint**: User Story 1 acceptance scenarios from [spec.md](spec.md) pass: fresh clone → `./mvnw clean verify` exits 0 → `./mvnw test` all green.

---

## Phase 4: User Story 2 — Server Starts and Connects to the Database (Priority: P1)

**Goal**: The Spring Boot application starts, reaches a ready state, and reports a successful datasource connection in its logs.

**Independent Test**: With Postgres running per `compose.yml`, `./mvnw spring-boot:run` reaches `Started IssueflowApplication` within 30s and the log contains a successful HikariCP pool start + Flyway "Successfully validated" line. Stopping the database before startup causes the app to fail fast with a clear error.

- [x] T014 [US2] `ApplicationStartupTest` written — `@SpringBootTest(RANDOM_PORT)`, asserts `/health` returns 200 with `status=UP`. **Passes** against H2 test profile.

**Checkpoint**: User Story 2 acceptance scenarios from [spec.md](spec.md) pass: `./mvnw spring-boot:run` reaches ready; `ApplicationStartupTest` passes in CI without Docker.

---

## Phase 5: User Story 3 — Health Endpoint Confirms the Plumbing (Priority: P2)

**Goal**: `GET /health` returns 200 with both components UP when healthy, 503 with the `db` component DOWN when the database is unreachable.

**Independent Test**: With the app and database running, `curl -s -i http://localhost:8080/health` returns 200 and a JSON body matching the healthy example in [contracts/health.openapi.yaml](contracts/health.openapi.yaml). Stopping the database container and re-querying returns 503 with the `db` component clearly marked DOWN.

- [x] T015 [US3] `HealthResponse` record + nested `ComponentStatus` record created with Javadoc.
- [x] T016 [US3] `HealthController` created — `@GetMapping("/health")`, injects `HealthEndpoint` + `Clock`, returns `HealthResponse` mapped from Actuator's `HealthComponent`/`CompositeHealth`. Added `ClockConfiguration` to supply a `Clock` bean. All magic values extracted to named constants. **NOTE**: switched `management.endpoints.web.exposure` from `exclude: "*"` to `include: health` because `@ConditionalOnAvailableEndpoint` requires the endpoint to be exposed via at least one technology for the bean to register. Side effect: `/actuator/health` also resolves now — both paths are valid, our `/health` remains the documented contract.
- [x] T017 [P] [US3] `HealthControllerTest` — pure JVM, Mockito-mocked `HealthEndpoint` + fixed `Clock`. Two sentence-named tests (`returnsTwoHundredAndUpWhenAllComponentsHealthy`, `returnsFiveHundredThreeAndDownWhenDatabaseUnreachable`), both green.
- [x] T018 [US3] `postman/issueflow.postman_collection.json` — Health folder updated with two requests: "Health — Healthy" (200 + status=UP + both components UP) and "Health — DB Down (manual)" (503 + db DOWN). Tickets folder left in place as your placeholder.

**Checkpoint**: User Story 3 acceptance scenarios from [spec.md](spec.md) pass: `curl /health` returns 200 healthy / 503 with db stopped; `HealthControllerTest` passes; Newman run of the collection passes (with the DB-down request executed manually).

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T019 [P] Javadoc audit done inline as code was written: every public method on `ErrorType`, `ProblemDetailFactory`, `GlobalExceptionHandler`, `HealthController`, `HealthResponse`, `ComponentStatus`, and `ClockConfiguration` has Javadoc with `@param`/`@return`/`@throws` as applicable. Private helpers (`extractComponent`, `toComponentStatus`, `build`) have no Javadoc — clean naming carries them.
- [x] T020 [P] Live verification executed (with Docker Desktop running per user). `docker compose up -d`; app reached READY in ~30s; `curl /health` → 200 with both components UP and a real ISO timestamp; `docker compose stop db` then `curl /health` → 503 with `db.status=DOWN` and a sanitized JDBC error in `detail`; `docker compose start db` restored the healthy path. Spring app stopped and port 8080 freed; Postgres + Adminer left running for further dev.
- [x] T021 [P] Constitution re-check (see report below) — all four gates still pass against the implemented code; no Complexity Tracking entries required.
- [x] T022 `./mvnw clean verify` → **BUILD SUCCESS**, 8 tests run / 0 failures / 0 errors / 0 skipped, Spotless reports "17 files clean — 0 needs changes," jar built at `target/issueflow-0.0.1-SNAPSHOT.jar`.
- [x] T023 `npx newman run postman/issueflow.postman_collection.json -e postman/issueflow.local.postman_environment.json --folder "Health"` — "Health — Healthy" passed all 4 assertions; "Health — DB Down (manual)" failed as designed (Newman runs every request; that variant is documented as requiring operator action to stop the DB first).

---

## Constitution Re-Check (T021)

| # | Gate | Status | Evidence |
|---|------|--------|----------|
| I  | Clean Code | ✓ | Function names reveal intent (`handleValidation`, `extractComponent`, `toComponentStatus`). No method exceeds ~15 lines. No flag arguments. Magic strings extracted (`STATUS_UP`, `COMPONENT_DB`, `ErrorType.*` URIs). No dead/commented-out code. |
| II | Testing Standards | ✓ | 6 pure-JVM unit tests (Mockito, no Spring) across `GlobalExceptionHandlerTest` (4) and `HealthControllerTest` (2). 1 narrowly-scoped Spring smoke test (`ApplicationStartupTest`). Postman "Health — Healthy" passes via Newman. Branches covered: every `@ExceptionHandler` and both `/health` outcomes. No tests written for trivial records or `ClockConfiguration`. |
| III| Documentation Discipline | ✓ | Javadoc present on every public method/class; absent on private helpers. `README.md` Health row added. `run.md` validated end-to-end via live `docker compose` run. `prompts.md` records model + four `/speckit-*` prompts honestly. |
| IV | API Consistency | ✓ | `GET /health` → 200/503 with documented JSON shape. RFC 7807 scaffold lives in `GlobalExceptionHandler` and uses Spring 6's built-in `ProblemDetail` with stable `type` URIs from `ErrorType`. camelCase JSON throughout (`status`, `components`, `timestamp`, `detail`). No JPA entity ever crosses a controller boundary (no entities exist yet, by design). Reused libraries: Spring Actuator (health), Bean Validation (ready for future endpoints), Flyway (over `ddl-auto`), Spring's `ProblemDetail` (over a third-party RFC 7807 lib). |

**Verdict**: PASS on all four gates. No Complexity Tracking entries needed.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: no dependencies — start immediately.
- **Phase 2 (Foundational)**: depends on Phase 1 (needs `pom.xml` to compile new classes and packages to exist). **Blocks all user stories.**
- **Phase 3 (US1)**: depends on Phase 2; produces documentation that mirrors the configuration written in Phase 2.
- **Phase 4 (US2)**: depends on Phase 2. Independent of US1's documentation.
- **Phase 5 (US3)**: depends on Phase 2. Independent of US1 and US2 in terms of code (US3 adds the controller; US2 only adds a test).
- **Phase 6 (Polish)**: depends on Phases 3, 4, 5 being complete.

### Within Phase 2 (Foundational)

- T004 and T005 touch different files → can run in parallel after T001.
- T006 (Flyway baseline) is independent of T004/T005 → parallel.
- T007 must complete before T008 (factory uses constants).
- T007 and T008 must both complete before T009 (handler uses factory + constants).
- T010 (handler test) depends on T009.

### Within each User Story phase

- US1: T011 (run.md) does not strictly depend on T012/T013 but they share doc surface — T012 and T013 can run in parallel with each other.
- US2: single task (T014).
- US3: T015 (DTO) before T016 (controller). T017 (test) parallel with T018 (Postman) once T016 exists.

### Parallel Opportunities

- **Phase 1**: T002 and T003 are `[P]` after T001.
- **Phase 2**: T005 || T006 after T004; T008 || (T007 first); T010 || nothing after T009.
- **Phase 3 (US1)**: T012 || T013 || T011 (different files entirely).
- **Phase 5 (US3)**: T017 || T018 once T016 lands.
- **Phase 6**: T019 || T020 || T021 all parallel.

---

## Parallel Example: User Story 3 (after T016 is done)

```bash
# Run these two together — different files, no shared state:
Task T017: HealthControllerTest at src/test/java/com/att/tdp/issueflow/common/health/HealthControllerTest.java
Task T018: postman/issueflow.postman_collection.json
```

## Parallel Example: User Story 1

```bash
# All three are doc files at the repo root — touch them in parallel:
Task T011: run.md
Task T012: README.md edit
Task T013: prompts.md
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2 — both are P1)

1. Phase 1 Setup (`pom.xml`, packages, postman dir).
2. Phase 2 Foundational (`application.yaml`, Flyway baseline, RFC 7807 scaffold + test).
3. Phase 3 US1 (docs).
4. Phase 4 US2 (startup smoke test).
5. **STOP and VALIDATE**: Fresh clone → `./mvnw clean verify` exits 0; `./mvnw spring-boot:run` boots against Postgres.

At this point the "compiles + connects + runs" promise of the spec is met.
The remaining story (US3) is P2 and turns the implicit "it works" into an
HTTP-checkable contract.

### Incremental Delivery

1. Setup + Foundational → foundation ready (no demo-able output yet).
2. Add US1 + US2 → demo: fresh clone → green build → app boots with healthy DB log lines → MVP ✅.
3. Add US3 → demo: `curl /health` returns 200; stop DB; re-curl returns 503 → full skeleton ✅.
4. Polish (Phase 6) → quickstart validated end-to-end; constitution gates re-checked.

### Parallel Team Strategy

With two developers after Foundational completes:

- Dev A: US1 (T011 → T012 || T013) — owns docs.
- Dev B: US2 (T014) and US3 (T015 → T016 → T017 || T018) — owns code + tests.

Stories integrate at the Polish phase. There is no shared file between US1
and US3 except `README.md` (US1 adds the Health row before any business
endpoint exists, so no merge conflict).

---

## Notes

- `[P]` tasks touch different files and have no dependency on an incomplete
  task. Verify file paths before parallelizing.
- Every test task names the exact test class file path so an LLM (or human)
  can create the file without re-reading the plan.
- Constitution Principle II makes T010, T014, T017, and T018 **mandatory**
  — they are not optional even though spec-kit defaults treat tests as
  optional. Do not skip them.
- Commit after each task or each tight group (e.g., T007+T008 together,
  T015+T016 together).
- Do not push to the remote until Phase 6 (T022) confirms a clean
  `./mvnw verify`.
