# AI Prompts Log

Honest record of how AI assisted this codebase. Constitution Principle III
requires this file to name the model and the key prompts that shaped each
slice of work.

## Model

- **Claude Opus 4.7** (`claude-opus-4-7`) via Claude Code, run inside the
  VS Code extension on Windows 11.

## Feature 001 — Project Skeleton

Driven by the Spec Kit `/speckit-*` pipeline. Each invocation is a single
prompt; the model's output is captured in `.specify/memory/constitution.md`,
`specs/001-project-skeleton/spec.md`, `…/plan.md`, and `…/tasks.md`.

### `/speckit-constitution`

> Principle 1 — Clean Code. Follow Uncle Bob's Clean Code: names reveal
> intent, functions are short and do one thing, one level of abstraction
> per function, no flag arguments, no dead or commented-out code, no
> magic numbers or strings, and comments explain why not what.
>
> Principle 2 — Testing Standards. Every feature ships with both
> code-level unit tests (JUnit 5 + Mockito, no Spring context) and Postman
> API tests. At least one or two simple sanity tests are mandatory; complex
> tests are mandatory wherever branching logic, state transitions,
> concurrency, or edge cases exist. Coverage target is small-to-medium —
> critical paths and tricky logic only, not trivial getters or mappers.
> Tests are named like sentences and follow Arrange-Act-Assert.
>
> Principle 3 — Documentation Discipline. Document public methods only with
> Javadoc covering purpose, parameters, return, and exceptions; private
> methods rely on clean naming. Every API endpoint is documented in the
> README, run.md always works from a fresh clone, and prompts.md honestly
> records AI model and key prompts.
>
> Principle 4 — API Consistency. Follow REST conventions strictly: correct
> verbs, correct status codes (201/204/404/409/422), plural nouns, RFC 7807
> Problem Details for errors with informative messages, camelCase JSON and
> snake_case SQL, consistent field naming (id, <entity>Id,
> createdAt/updatedAt), DTOs never expose entities, and pagination uses the
> same shape everywhere. also use popular libraries where seems necessary

Produced `.specify/memory/constitution.md` v1.0.0 ratified 2026-05-20.

### `/speckit-specify`

> cover project so it compiles, connects to a database, and runs. Nothing
> useful happens yet, but the foundation is in place: a running server, a
> running database, and the plumbing between them. Think of it as building
> the empty house — walls, plumbing, electricity — before any furniture
> goes in.

Produced `specs/001-project-skeleton/spec.md` with 3 user stories (P1/P1/P2),
10 functional requirements, 6 measurable success criteria, and explicit
Out-of-Scope and Assumptions sections. No `[NEEDS CLARIFICATION]` markers
remained.

### `/speckit-plan`

> Spring Boot 3.4 + Java 21 project initialized via Spring Initializr or the
> provided skeleton, built with Maven (mvnw). Core dependencies:
> spring-boot-starter-web, spring-boot-starter-data-jpa,
> spring-boot-starter-validation, postgresql driver, lombok, and
> spring-boot-starter-test with JUnit 5 and Mockito. PostgreSQL via the
> provided compose.yml. Use Flyway (flyway-core + flyway-database-postgresql)
> for schema migrations rather than ddl-auto=update — migrations are
> reviewable and version-controlled. Configure application.yml with
> datasource properties and Hibernate dialect. Add a global exception
> handler scaffold (@RestControllerAdvice) returning RFC 7807 ProblemDetail
> responses, ready to be extended in later phases. Project structure:
> package-by-feature (user, project, ticket, comment, auth, common) rather
> than package-by-layer.

Produced `plan.md`, `research.md` (8 decisions, including Spring Actuator
for health, Flyway over `ddl-auto`, Spring's built-in `ProblemDetail`),
`data-model.md`, two contract files under `contracts/`, and `quickstart.md`.

### `/speckit-tasks`

(no prompt argument)

Produced `tasks.md` — 23 tasks across 6 phases, organized by user story
with explicit `[P]` parallel markers and exact file paths.

### `/speckit-implement`

(no prompt argument)

Executed the 23 tasks. Notable manual corrections during execution:

- The actual Spring Boot class is `IssueFlowApplication` (capital `F`), not
  `IssueflowApplication` as the plan assumed. The `ApplicationStartupTest`
  uses the real class name.
- `pom.xml` had a Spotless / Google Java Format plugin added between
  `/speckit-plan` and `/speckit-implement`. Java sources were run through
  `./mvnw spotless:apply` after writing to satisfy the format check.
- The existing `postman/` collection had placeholder Tickets requests
  pre-authored; T018 only replaced the Health folder, leaving Tickets in
  place for future features.

## Feature 002 — Users CRUD

Driven by the same Spec Kit `/speckit-*` pipeline.

### `/speckit-specify`

> Add the concept of a person to the system. You can create users, fetch
> them, list them all, update their name or role, and delete them. Each
> user has a username, email, full name, and a role that is either ADMIN
> or DEVELOPER. No login yet — this phase just establishes who exists in
> the system.

Produced `specs/002-users-crud/spec.md` with 5 user stories (3× P1, 2× P2),
14 FRs, 7 SCs. No `[NEEDS CLARIFICATION]` markers.

### `/speckit-plan`

> Spring Boot 3.4 + Java 21 project initialized via Spring Initializr or
> the provided skeleton, built with Maven (mvnw). … User JPA entity with
> @Entity, UUID or auto-increment Long id (pick one and use it
> consistently across all entities — UUIDs are easier with distributed
> systems but Long is simpler; recommend Long here). Role as a Java enum
> stored via @Enumerated(EnumType.STRING). Spring Data JPA UserRepository
> extends JpaRepository<User, Long>. DTOs for request/response
> (CreateUserRequest, UpdateUserRequest, UserResponse) — never expose
> entities directly. Use MapStruct (mapstruct + mapstruct-processor) for
> entity↔DTO mapping. Validation via jakarta.validation annotations on
> DTOs, triggered with @Valid. Password field: add a passwordHash column
> now (even though login comes in Phase 2). Unique constraints on
> username and email at both the JPA level and via Flyway migration. Unit
> tests: service-layer tests with Mockito mocking the repository.

Produced `plan.md`, `research.md` (10 decisions), `data-model.md`, two
contract files, `quickstart.md`.

### `/speckit-tasks`

(no prompt argument; ran under plan-mode review)

Produced `tasks.md` — 36 tasks across 8 phases. One question asked of the
user before exiting plan mode: keep URL-form `type` URIs vs switch to
relative. User chose to keep URL form.

### `/speckit-implement`

(no prompt argument)

Executed the 36 tasks. Notable manual corrections during execution:

- The plan listed T013/T014 (DTOs) inside US1, but T012 (UserMapper)
  requires them to compile. Did T013 + T014 at the Phase 2/3 boundary so
  the mapper could land in Phase 2 as planned.
- Plan said `@Column(unique = true)` on entity fields. Kept the
  annotation as field-level documentation but the actual uniqueness is
  enforced by Flyway functional unique indexes on `LOWER(...)` — the JPA
  annotation does nothing at runtime with `ddl-auto: none`.
- All five `UserService` methods landed in a single `Write` pass; the
  per-story plan structure was preserved in commits / test coverage
  rather than in incremental file edits, since adding methods one at a
  time to the same file would require five sequential edits without
  any reviewer benefit.
- `UpdateUserRequest` uses `@AssertTrue` on `hasAtLeastOneFieldSet()` to
  enforce "at least one of fullName or role" — Bean Validation surfaces
  this as a 422 ProblemDetail with `errors[].field = "hasAtLeastOneFieldSet"`,
  which clients should interpret as "request was empty."

## Feature 003 — JWT Authentication & Authorization

Model: **claude-sonnet-4-6** via Claude Code (VS Code extension), 2026-05-21.
Driven by the same Spec Kit `/speckit-*` pipeline.

### `/speckit-specify`

> Add JWT-based authentication. Users log in with username and password and
> receive a signed JWT. The token protects all existing endpoints — any
> request without a valid token gets a 401. Two more auth endpoints:
> GET /auth/me to retrieve the caller's own profile, and POST /auth/logout
> as a no-op (stateless, client discards the token).

Produced `specs/003-jwt-auth/spec.md` with 4 user stories (P1/P1/P2/P3).

### `/speckit-plan`

> Spring Boot 3.4 + Java 21. Add spring-boot-starter-security (Spring
> Security 6.4.x) — removes the spring-security-crypto direct dependency
> which becomes transitive. Add io.jsonwebtoken jjwt-api:0.12.6 (compile),
> jjwt-impl:0.12.6 (runtime), jjwt-jackson:0.12.6 (runtime). HS256 signing
> key derived from a configurable secret (min 32 chars). Token TTL:
> PT12M (configurable). No schema change — password_hash column already
> exists. Seed users via V3__seed_test_users.sql with bcrypt-hashed
> passwords. Authority model: plain role names (ADMIN, DEVELOPER) with no
> ROLE_ prefix — future @PreAuthorize must use hasAuthority('ADMIN').

Key decisions: stateless (no HttpSession), custom RFC 7807
AuthenticationEntryPoint for 401, `AuthController` directly injects
`UserRepository` to access `userId` and `role` for JWT claims (no
intermediate AuthService — see `docs/decisions.md`).

Produced `plan.md`, `research.md`, `data-model.md`, `contracts/auth.openapi.yaml`, `quickstart.md`.

### `/speckit-tasks`

(no prompt argument)

Produced `tasks.md` — 25 tasks across 6 phases with explicit `[P]`
parallel markers.

### `/speckit-implement`

(no prompt argument)

Executed all 25 tasks. Notable implementation notes:

- `SecurityConfiguration` uses an inner `Rfc7807AuthenticationEntryPoint`
  that serializes a `LinkedHashMap` via Jackson — avoids the Zalando
  Problem library dependency while staying RFC 7807 compliant.
- `AuthController` builds a throwaway `UserDetails` from the `User` entity
  to call `jwtService.issueToken` rather than loading it a second time from
  `UserDetailsService`. Cleaner than a double DB round-trip.
- `JwtAuthenticationFilter` only sets the `SecurityContextHolder` when it
  is currently empty — prevents re-authentication on every hop if a filter
  earlier in the chain has already populated it.
- Unit tests are pure JVM: `JwtServiceTest` constructs `JwtService` directly
  with a test `JwtProperties`; `IssueFlowUserDetailsServiceTest` mocks
  `UserRepository` with Mockito.

## Feature 004 — Projects CRUD

Model: **claude-sonnet-4-6** via Claude Code (VS Code extension), 2026-05-21.
Driven by the same Spec Kit `/speckit-*` pipeline.

### `/speckit-specify`

> Add the concept of a project to the system. Users can create projects,
> fetch a single project, list all active projects, update a project's name
> or description, and soft-delete it. Each project has a name (unique
> among active projects, case-insensitive), an optional description, and
> an owner (an existing user). Deletion is soft: a deleted_at timestamp
> is set, and deleted projects are invisible to all standard reads.

Produced `specs/004-projects-crud/spec.md` with 4 user stories (2× P1, 2× P2).

### `/speckit-plan`

> Spring Boot 3.4 + Java 21. No new dependencies — Spring Data JPA,
> MapStruct, Bean Validation already in pom.xml. New Flyway migration
> V5__projects.sql: projects table with id, name, description, owner_id
> (FK → users), created_at, updated_at, deleted_at; partial unique index
> ux_projects_name_active ON projects(LOWER(name)) WHERE deleted_at IS NULL.
> Project entity with @ManyToOne(fetch=LAZY) owner. ProjectMapper via
> MapStruct: toEntity takes both CreateProjectRequest and resolved User
> to keep mapper free of repository calls. All five endpoints return 200 OK
> per the README Projects APIs table (canonical template overrides default
> REST conventions).

Produced `plan.md`, `research.md`, `data-model.md`,
`contracts/projects.openapi.yaml`, `quickstart.md`.

### `/speckit-tasks`

(no prompt argument)

Produced `tasks.md` — 25 tasks across 7 phases organized by user story
with explicit `[P]` parallel markers and dependency graph.

### `/speckit-implement`

(no prompt argument)

Executed all 25 tasks. Notable implementation notes:

- `ProjectService` and `ProjectController` were written in full in a single
  pass rather than incrementally by user story, since all methods target the
  same files and sequential edits add no reviewer benefit.
- `ProjectMapper.toEntity` takes two parameters (`CreateProjectRequest` +
  resolved `User`) — MapStruct multi-source mapping with explicit `@Mapping`
  for each server-managed field (id, createdAt, updatedAt, deletedAt).
- `UpdateProjectRequest` uses `@AssertTrue` on `hasAtLeastOneFieldSet()` to
  reject empty `{}` bodies; Bean Validation surfaces this as a 400.
- All reads use `findByIdAndDeletedAtIsNull` — soft-deleted projects are
  invisible at the repository boundary, not filtered in the service layer.
- The Postman Projects folder uses `pm.collectionVariables.set('createdProjectId', ...)`
  to pass the created id across requests (create → get → update → delete → re-delete).
