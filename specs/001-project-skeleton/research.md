# Phase 0 — Research: Project Skeleton

This phase resolves every open technical question for the skeleton. There are
no `[NEEDS CLARIFICATION]` markers in the spec; the items below capture
deliberate choices and their rejected alternatives so future readers can see
why the skeleton looks the way it does.

---

## R1. Health endpoint: Actuator vs. custom controller

- **Decision**: Use Spring Boot Actuator (`spring-boot-starter-actuator`) and
  expose its built-in `health` endpoint at the path `/health`. Enable the
  `db` health indicator (on by default once a `DataSource` is on the
  classpath) and configure `management.endpoint.health.show-components=always`
  so callers see whether the DB is the failing component.
- **Rationale**:
  - Constitution Principle IV says "prefer popular, well-maintained libraries."
    Actuator is the canonical Spring Boot health-check mechanism and ships
    with a `DataSource` health indicator that already runs `SELECT 1` against
    Postgres. Hand-rolling this is busywork that adds no signal.
  - Actuator emits `200 UP` / `503 DOWN`, which is exactly the
    "success / non-success" contract the spec demands (FR-006).
  - Path remapping is trivial: `management.endpoints.web.base-path: /` plus
    `management.endpoints.web.path-mapping.health: health` exposes it at
    `/health` rather than `/actuator/health`.
- **Alternatives considered**:
  - **Hand-rolled `HealthController`**: rejected because we would reimplement
    DB liveness probing that Actuator already does, and we would diverge from
    every other Spring Boot project on this team going forward.
  - **`/actuator/health` (default path)**: workable, but the spec's
    Assumptions section already uses `/health`. We do not need the
    `/actuator/*` prefix because we are not exposing other management
    endpoints in this feature.

> **Note**: A thin `HealthController` is **still** planned in
> `common.health` as a documentation surface (Javadoc, OpenAPI annotation,
> integration test target). It can delegate to the Actuator endpoint or
> simply coexist with the remapped Actuator path. Final decision is at
> implementation time; both options satisfy the contract.

---

## R2. Schema management: Flyway, not `ddl-auto=update`

- **Decision**: Add `flyway-core` and `flyway-database-postgresql` to
  `pom.xml`. Set `spring.jpa.hibernate.ddl-auto=none` in `application.yaml`.
  Create `src/main/resources/db/migration/V1__baseline.sql` containing only a
  SQL comment (no schema objects yet — the skeleton has no entities).
- **Rationale**:
  - User input explicitly mandates Flyway over `ddl-auto`. This is also the
    spirit of constitution Principle III (documentation discipline) and
    Principle IV (reviewable, version-controlled change) applied to the
    database.
  - Flyway with zero migrations would still work (it would just create
    `flyway_schema_history`), but having `V1__baseline.sql` makes the
    migration directory discoverable and gives future migrations a clear
    starting point.
  - `ddl-auto=none` is safer than `validate` while there are no JPA
    entities — `validate` would fail at startup against an empty schema.
    A later feature with entities will switch this to `validate`.
- **Alternatives considered**:
  - **Liquibase**: comparable functionality, less idiomatic in Spring Boot
    projects this team has built before. Flyway has a tighter Spring Boot
    auto-configuration.
  - **`spring.flyway.baseline-on-migrate=true` with no V1**: works, but
    leaves no breadcrumb in the migrations folder for new contributors.

---

## R3. RFC 7807 Problem Details: built-in `ProblemDetail` vs. Zalando Problem

- **Decision**: Use Spring Framework 6.x's built-in
  `org.springframework.http.ProblemDetail`. No third-party library.
- **Rationale**:
  - Spring 6 (shipped with Spring Boot 3.x) added first-class support for
    RFC 7807. `ProblemDetail` is the standard, content-negotiated
    (`application/problem+json`) and integrates with `ResponseEntity` and
    `@ExceptionHandler`.
  - Adding `zalando/problem-spring-web` would duplicate framework
    functionality for marginal ergonomic gain and pin us to an additional
    transitive dependency.
- **Alternatives considered**:
  - **`zalando/problem-spring-web`**: rejected — Spring's built-in equivalent
    covers this feature's needs.
  - **Hand-rolled error DTOs**: rejected outright by Principle IV.

---

## R4. Test strategy for the skeleton

- **Decision**:
  1. **Unit tests** in `src/test/java`, pure-JVM (no Spring context, no
     `@SpringBootTest`, no `@DataJpaTest`):
     - `HealthControllerTest` — verifies the controller's response shape
       for healthy and unhealthy states.
     - `GlobalExceptionHandlerTest` — covers each `@ExceptionHandler`
       branch (e.g., `MethodArgumentNotValidException` → 422 ProblemDetail,
       generic `Exception` → 500 ProblemDetail with safe `detail`).
       Mockito mocks any collaborators.
  2. **One Spring-context smoke test**: `ApplicationStartupTest`
     (`@SpringBootTest(webEnvironment = RANDOM_PORT)`) proves the context
     loads against an in-memory H2 (with `MODE=PostgreSQL`) so CI does not
     need Docker for the unit suite. This is the *only* place Spring
     context appears in this feature's tests — narrowly scoped, per the
     constitution's allowance.
  3. **Postman API tests** in `postman/issueflow.postman_collection.json`,
     runnable via Newman. Two requests for `GET /health` (steady-state
     healthy; documented manual variant for DB-down).
- **Rationale**:
  - Principle II requires both layers (unit + Postman). The Spring-context
    smoke test is the minimal way to satisfy User Story 1 (the application
    actually starts) without making the rest of the suite slow.
  - Pure-JVM unit tests over the exception handler are where the *branching
    logic* lives (Principle II: "complex tests MANDATORY wherever branching
    exists").
- **Alternatives considered**:
  - **Skip the Spring smoke test, rely on `mvn spring-boot:run` manually**:
    rejected — it puts the "does it start?" check outside the test suite,
    defeating the whole point.
  - **Use Testcontainers Postgres** for the smoke test: deferred. Adds CI
    dependencies (Docker-in-Docker) that the skeleton does not need.
    Re-evaluate when migrations begin testing real Postgres semantics.

---

## R5. Configuration: environment variables vs. profile files

- **Decision**: `application.yaml` holds the local-default datasource
  (`jdbc:postgresql://localhost:5432/issueflow`, user `issueflow`, password
  `issueflow`), but each value is overridable via environment variable using
  Spring's standard placeholder syntax — e.g.,
  `${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/issueflow}`. No
  separate `application-prod.yaml`; environment is the deployment seam.
- **Rationale**:
  - Spec FR-003 requires "no code changes to point at a different database."
  - Spec FR-010 requires "refuse to start if a required value is missing."
    Spring's placeholder-with-default lets us choose between "safe default
    for local dev" and "must be set in prod" per key. Production deploys
    will leave the defaults in place but set the env vars explicitly.
- **Alternatives considered**:
  - **Profile files (`application-prod.yaml`, `application-dev.yaml`)**:
    deferred. Useful once we actually have multiple environments — premature
    for the skeleton.
  - **`.env` files via `spring-boot-dotenv` or similar**: out of scope, adds
    a niche dependency.

---

## R6. Lombok policy

- **Decision**: Use Lombok minimally and only where it does not hide intent.
  Allowed: `@Getter` / `@Setter` on small data carriers, `@Slf4j` for
  loggers. Disallowed in this feature: `@Data` on multi-field classes (hides
  `equals/hashCode` contracts), `@Builder` (overkill for the DTOs we are
  creating). `HealthResponse` is a Java `record`, not a Lombok class.
- **Rationale**: Constitution Principle I ("functions are short, names
  reveal intent, comments explain WHY") — Lombok is fine when its generated
  code matches what a developer would read in the source. It is not fine
  when readers must memorize annotation behavior to understand the class.

---

## R7. JPA dialect configuration

- **Decision**: Set
  `spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect`
  explicitly in `application.yaml` (per user input). Remove `database:
  POSTGRESQL` from the existing yaml since Spring Boot 3.x infers dialect
  from the JDBC URL but the explicit dialect property is the modern,
  unambiguous form.
- **Rationale**: User input names "configure application.yml with datasource
  properties and Hibernate dialect" explicitly. Being explicit removes
  guesswork during future debugging.
- **Alternatives considered**: Letting Spring Boot auto-detect — works in
  3.x, but adds one less line of "where is this configured?" friction at no
  cost.

---

## R8. Existing `application.yaml` cleanup

- **Decision**: Rewrite `src/main/resources/application.yaml` in this
  feature:
  - Remove `spring.datasource.platform: postgres` (deprecated property).
  - Remove `spring.sql.init.mode: always` (conflicts with Flyway — we want
    Flyway to manage schema, not `schema.sql`/`data.sql`).
  - Change `spring.jpa.hibernate.ddl-auto: update` → `none`.
  - Add Flyway block enabling migrations and the explicit Hibernate
    dialect.
  - Add the Actuator block exposing `health` at `/health`.
- **Decision (test resources)**: Rewrite `src/test/resources/application.yaml`
  to use H2 with `MODE=PostgreSQL`, disable Flyway (`spring.flyway.enabled:
  false`) since the test starts against an empty H2 and the skeleton has no
  migrations worth running there. Remove the stray `spring.sql.init.platform:
  mssql` line — that is left over from a template and has no business in this
  project.
- **Rationale**: The current YAML is template residue ("mssql" platform on a
  Postgres app is a smell). Bringing both files into a clean, intentional
  state is part of "building the empty house."

---

## Open Questions / NEEDS CLARIFICATION

None.
