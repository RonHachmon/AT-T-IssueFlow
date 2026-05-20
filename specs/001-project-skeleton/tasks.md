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

- [ ] T001 Update [pom.xml](pom.xml) to add `spring-boot-starter-actuator`, `org.flywaydb:flyway-core`, and `org.flywaydb:flyway-database-postgresql`. Leave the existing `h2` dependency in `runtime` scope (used by the test profile per [research.md](research.md) R4). Confirm `java.version` stays at `21`.
- [ ] T002 [P] Create the package-by-feature scaffold by adding empty `package-info.java` files at: `src/main/java/com/att/tdp/issueflow/user/package-info.java`, `src/main/java/com/att/tdp/issueflow/project/package-info.java`, `src/main/java/com/att/tdp/issueflow/ticket/package-info.java`, `src/main/java/com/att/tdp/issueflow/comment/package-info.java`, `src/main/java/com/att/tdp/issueflow/auth/package-info.java`, `src/main/java/com/att/tdp/issueflow/common/package-info.java`. Each file contains the package declaration and a one-line Javadoc naming the slice (e.g., `/** User feature slice. */`).
- [ ] T003 [P] Create the empty Postman directory placeholder: ensure `postman/` exists at the repo root. The collection file is populated in T018.

**Checkpoint**: Project compiles (`./mvnw clean compile` exits 0) with the new dependencies wired in.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Replace the template-residue `application.yaml`, wire Flyway,
and ship the RFC 7807 `@RestControllerAdvice` scaffold. **No user story
work can begin until this phase is complete.**

- [ ] T004 Rewrite [src/main/resources/application.yaml](src/main/resources/application.yaml) per [research.md](research.md) R5, R7, R8: Postgres datasource with each value overridable via env var (e.g., `${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/issueflow}`), `spring.jpa.hibernate.ddl-auto: none`, explicit `spring.jpa.properties.hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect`, Flyway enabled, Actuator block configured so `health` is the only exposed endpoint (`management.endpoints.web.exposure.include: health`, `management.endpoint.health.show-components: always`). Remove the deprecated `spring.datasource.platform: postgres` and the conflicting `spring.sql.init.mode: always`.
- [ ] T005 [P] Rewrite [src/test/resources/application.yaml](src/test/resources/application.yaml) per [research.md](research.md) R8: H2 with `MODE=PostgreSQL`, `spring.flyway.enabled: false`, `spring.jpa.hibernate.ddl-auto: create-drop`. Remove the stray `spring.sql.init.platform: mssql`.
- [ ] T006 [P] Create the Flyway baseline migration at `src/main/resources/db/migration/V1__baseline.sql` containing only a SQL comment: `-- IssueFlow baseline. No schema objects yet; entities arrive with later features.`
- [ ] T007 Create `src/main/java/com/att/tdp/issueflow/common/error/ErrorType.java` as a `public final class` with `private` constructor and `public static final String` constants for the stable problem `type` URIs listed in [contracts/problem-details.md](contracts/problem-details.md): `NOT_FOUND`, `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `CONFLICT`, `INTERNAL_ERROR`. Javadoc on the class explains the stability contract; each constant has a one-line Javadoc naming the status code.
- [ ] T008 [P] Create `src/main/java/com/att/tdp/issueflow/common/error/ProblemDetailFactory.java` with one `public static ProblemDetail` factory per `ErrorType` constant (e.g., `notFound(String detail, URI instance)`), each returning a fully populated `org.springframework.http.ProblemDetail`. No flag arguments (Principle I). Javadoc on every `public` method covers `@param`, `@return`.
- [ ] T009 Create `src/main/java/com/att/tdp/issueflow/common/error/GlobalExceptionHandler.java` as a `@RestControllerAdvice`. Depends on T007 and T008. Implement `@ExceptionHandler` methods for: `MethodArgumentNotValidException` → 422 + `validation-failed` with an `errors` extension array; `HttpMessageNotReadableException` → 400 + `malformed-request`; `org.springframework.web.servlet.resource.NoResourceFoundException` → 404 + `not-found`; `Exception` (catch-all) → 500 + `internal-error` (log the stack server-side, do not leak it in `detail`). Javadoc on every public handler method documents purpose, params, return, thrown exceptions.
- [ ] T010 [P] Create `src/test/java/com/att/tdp/issueflow/common/error/GlobalExceptionHandlerTest.java`. Pure JVM, JUnit 5 + Mockito, **no Spring context**. One test per `@ExceptionHandler` branch in T009. Test names read as sentences (e.g., `returnsValidationFailedProblemDetailWhenMethodArgumentInvalid`), Arrange-Act-Assert structure with blank-line separation.

**Checkpoint**: Foundation ready. `./mvnw test` runs `GlobalExceptionHandlerTest` and it passes. User story implementation can now begin.

---

## Phase 3: User Story 1 — Fresh Clone Builds Successfully (Priority: P1) 🎯 MVP

**Goal**: A developer who has never seen this repository can clone it, follow `run.md`, and end up with a green build on the first attempt.

**Independent Test**: On a clean machine with Java 21 + Docker installed: `git clone … && cd issueflow-java && ./mvnw clean verify` exits zero, and `./mvnw test` shows all unit tests passing.

- [ ] T011 [US1] Write [run.md](run.md) at the repo root documenting, in order: prerequisites (Java 21, Docker, free ports), how to start the database (`docker compose up -d`), how to build (`./mvnw clean verify`), how to run (`./mvnw spring-boot:run`), how to run the tests (`./mvnw test`), and how to hit the health endpoint (`curl -s -i http://localhost:8080/health`). The file MUST work end-to-end from a fresh clone; mirror [quickstart.md](quickstart.md) and verify each command before merge.
- [ ] T012 [P] [US1] Update [README.md](README.md) to add a `### Health API` section before `### Users APIs` with a single row: `GET /health` → `200 OK` (healthy) or `503 Service Unavailable` (db down), response body matching [contracts/health.openapi.yaml](contracts/health.openapi.yaml).
- [ ] T013 [P] [US1] Create [prompts.md](prompts.md) at the repo root with the format prescribed by constitution Principle III: name the AI model used (Claude Opus 4.7) and record the key `/speckit-*` prompts that produced the constitution, spec, plan, and tasks for this feature. One short entry per command invocation; do not embellish.

**Checkpoint**: User Story 1 acceptance scenarios from [spec.md](spec.md) pass: fresh clone → `./mvnw clean verify` exits 0 → `./mvnw test` all green.

---

## Phase 4: User Story 2 — Server Starts and Connects to the Database (Priority: P1)

**Goal**: The Spring Boot application starts, reaches a ready state, and reports a successful datasource connection in its logs.

**Independent Test**: With Postgres running per `compose.yml`, `./mvnw spring-boot:run` reaches `Started IssueflowApplication` within 30s and the log contains a successful HikariCP pool start + Flyway "Successfully validated" line. Stopping the database before startup causes the app to fail fast with a clear error.

- [ ] T014 [US2] Create `src/test/java/com/att/tdp/issueflow/ApplicationStartupTest.java`. This is the **only** Spring-context test in this feature (constitution Principle II's narrow exception). Use `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`, the test profile (H2 from T005), and a `TestRestTemplate` to assert that `GET /health` returns `200 OK` with body `status == "UP"`. Test name reads as a sentence: `applicationContextLoadsAndHealthEndpointReportsUp`.

**Checkpoint**: User Story 2 acceptance scenarios from [spec.md](spec.md) pass: `./mvnw spring-boot:run` reaches ready; `ApplicationStartupTest` passes in CI without Docker.

---

## Phase 5: User Story 3 — Health Endpoint Confirms the Plumbing (Priority: P2)

**Goal**: `GET /health` returns 200 with both components UP when healthy, 503 with the `db` component DOWN when the database is unreachable.

**Independent Test**: With the app and database running, `curl -s -i http://localhost:8080/health` returns 200 and a JSON body matching the healthy example in [contracts/health.openapi.yaml](contracts/health.openapi.yaml). Stopping the database container and re-querying returns 503 with the `db` component clearly marked DOWN.

- [ ] T015 [US3] Create `src/main/java/com/att/tdp/issueflow/common/health/HealthResponse.java` as a Java `record` with fields `String status`, `Map<String, ComponentStatus> components`, `Instant timestamp`, and a nested `record ComponentStatus(String status, String detail)`. Field shapes match [data-model.md](data-model.md). No Lombok. Javadoc on the outer record and the nested record covers purpose and field meanings.
- [ ] T016 [US3] Create `src/main/java/com/att/tdp/issueflow/common/health/HealthController.java` as `@RestController` mapping `GET /health`. Depends on T015. Inject `org.springframework.boot.actuate.health.HealthEndpoint`, call `healthEndpoint.health()`, and map the result to `HealthResponse` (status `UP` → 200; anything else → 503 via `ResponseEntity.status(...)`). Inject `java.time.Clock` to make `timestamp` testable. Javadoc on the public method documents purpose, return, and the 200/503 contract; no flag arguments; no magic strings (use constants from a new private static class or pull from `ErrorType`/`HealthResponse` itself).
- [ ] T017 [P] [US3] Create `src/test/java/com/att/tdp/issueflow/common/health/HealthControllerTest.java`. Pure JVM, JUnit 5 + Mockito, **no Spring context**. Mock `HealthEndpoint` and a fixed `Clock`. Two tests at minimum, sentence-named: `returnsTwoHundredAndUpWhenAllComponentsHealthy` and `returnsFiveHundredThreeAndDownWhenDatabaseUnreachable`. Arrange-Act-Assert with blank-line separation.
- [ ] T018 [US3] Create [postman/issueflow.postman_collection.json](postman/issueflow.postman_collection.json) populated with: (1) `GET {{baseUrl}}/health` request named "Health — Healthy" with a Postman test asserting `pm.response.to.have.status(200)` and `pm.expect(pm.response.json().status).to.eql("UP")`; (2) `GET {{baseUrl}}/health` request named "Health — DB Down (manual)" with a comment in the request description explaining the operator must stop the database container before running it, and a test asserting `pm.response.code === 503` and `pm.expect(pm.response.json().components.db.status).to.eql("DOWN")`. Collection variable `baseUrl` defaults to `http://localhost:8080`.

**Checkpoint**: User Story 3 acceptance scenarios from [spec.md](spec.md) pass: `curl /health` returns 200 healthy / 503 with db stopped; `HealthControllerTest` passes; Newman run of the collection passes (with the DB-down request executed manually).

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T019 [P] Audit Javadoc coverage per constitution Principle III: every `public` method on `HealthController`, `HealthResponse`, `GlobalExceptionHandler`, `ProblemDetailFactory`, and `ErrorType` has Javadoc with `@param`, `@return`, `@throws` (as applicable). Private methods MUST NOT have Javadoc.
- [ ] T020 [P] Validate [quickstart.md](quickstart.md) end-to-end from a fresh clone on a clean machine (or scratch directory): run all 7 numbered steps; every command must succeed without manual fixes. Update quickstart and `run.md` if any step needs correction.
- [ ] T021 [P] Re-evaluate the Constitution Check in [plan.md](plan.md) against the now-implemented code. Confirm every gate (I Clean Code, II Testing, III Docs, IV API Consistency) still passes; if any has slipped, fix the code rather than the gate.
- [ ] T022 Run `./mvnw clean verify` from the repo root and confirm exit code 0 with all tests green. Capture the test count in the PR description.
- [ ] T023 Run `npx newman run postman/issueflow.postman_collection.json --folder "Health — Healthy"` (with the app + DB running) and confirm 1 request, 0 failures. Document the manual DB-down run separately.

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
