# Feature Specification: Project Skeleton

**Feature Branch**: `001-project-skeleton`
**Created**: 2026-05-20
**Status**: Draft
**Input**: User description: "cover project so it compiles, connects to a database, and runs. Nothing useful happens yet, but the foundation is in place: a running server, a running database, and the plumbing between them. Think of it as building the empty house — walls, plumbing, electricity — before any furniture goes in."

## User Scenarios & Testing *(mandatory)*

> The "users" for this foundational feature are the people and systems that
> consume the repository before any business functionality exists: developers
> who clone it, reviewers who validate PRs, and the CI pipeline that gates
> merges. The feature delivers no end-user value on its own; it is the
> precondition for every story that follows.

### User Story 1 - Fresh Clone Builds Successfully (Priority: P1)

A developer who has never seen the repository before can clone it, follow
`run.md`, and end up with a green build on the first attempt. They do not
have to read source code, fix dependencies, or guess at configuration.

**Why this priority**: If a fresh clone cannot build, nothing else in the
project is reachable. This is the foundation under the foundation.

**Independent Test**: On a clean machine with Java 21 and Docker installed,
run the commands in `run.md` from a fresh clone. The build command exits
zero and produces a runnable artifact.

**Acceptance Scenarios**:

1. **Given** a clean working directory and only the prerequisites listed in
   `run.md`, **When** the developer runs the documented build command,
   **Then** the command exits with status zero and produces a runnable
   application artifact.
2. **Given** the project has been built, **When** the developer runs the
   documented test command, **Then** all tests in the suite pass.

---

### User Story 2 - Server Starts and Connects to the Database (Priority: P1)

A developer can start the application locally and observe that it has
successfully connected to a running database. The two halves of the system —
the application process and the database process — are wired together by
configuration shipped in the repository, not by ad-hoc setup steps the
developer has to invent.

**Why this priority**: "Compiles" without "runs and connects" is a half-built
house. Every feature past this one assumes a working datasource.

**Independent Test**: After bringing up the database container documented in
`run.md` and starting the application with the documented command, the
application log indicates a successful connection to the database and the
process stays alive (no startup exception, no crash-loop).

**Acceptance Scenarios**:

1. **Given** the database container is running per `run.md`, **When** the
   developer starts the application with the documented command, **Then**
   the application reaches a steady "ready to serve requests" state within
   30 seconds and does not exit.
2. **Given** the application is running, **When** the developer inspects the
   application log, **Then** the log contains a clear indication that the
   datasource connected successfully (no `Connection refused`, no
   `HikariPool ... failed to initialize`).
3. **Given** the database container is stopped, **When** the developer
   starts the application, **Then** the application fails fast with a clear
   error message naming the unreachable datasource — it does not start in a
   silently broken state.

---

### User Story 3 - Health Endpoint Confirms the Plumbing (Priority: P2)

An operator (or a CI smoke test) can issue a single HTTP request to confirm
that the running application is alive and its database connection is
healthy. They do not need to read logs or attach a debugger to verify
liveness.

**Why this priority**: The health endpoint is what turns "I think it's
running" into "I can prove it's running." It is also the single endpoint
that gives `/speckit-plan` something concrete to plan against and gives
Principle II of the constitution its first Postman target.

**Independent Test**: With the application running, sending an HTTP GET to
the health endpoint returns a success status and a payload indicating the
database is reachable. Stopping the database and re-querying returns a
non-success status.

**Acceptance Scenarios**:

1. **Given** the application is running and the database is reachable,
   **When** an HTTP client issues `GET /health`, **Then** the response
   status is `200 OK` and the body indicates that the application and
   database are both healthy.
2. **Given** the application is running but the database has become
   unreachable, **When** an HTTP client issues `GET /health`, **Then** the
   response status indicates the system is not healthy (a non-2xx code) and
   the body identifies the database as the failing component.

---

### Edge Cases

- **Wrong Java version**: A developer with a Java version older than the one
  required by the project attempts to build. The build fails with a
  human-readable error that names the required version — not a stack trace
  about unknown class file format.
- **Database container started but not yet accepting connections**: The
  application attempts to connect during the database's startup grace
  period. The application retries on a bounded schedule before failing fast,
  rather than crashing on the first attempt or retrying forever.
- **Port already in use**: The HTTP port or database port is occupied by
  another process. The application surfaces a clear "port in use" message
  identifying the conflicting port.
- **Two developers, two clones, one machine**: Two simultaneous local
  setups on the same machine do not silently share state — either each
  isolates its own database instance, or `run.md` calls out the conflict.
- **Stale build artifacts**: Running the build twice in succession (without
  cleaning) still produces a working artifact; no manual `rm -rf` needed
  between runs.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST build to a runnable artifact from a fresh
  clone using only the commands documented in `run.md`, with no manual edits
  to source or configuration required.
- **FR-002**: The repository MUST ship a documented mechanism for starting a
  local database instance suitable for development (so a new developer does
  not have to install or configure a database by hand).
- **FR-003**: The application MUST read its database connection settings
  from configuration that does not require code changes to point at a
  different database (e.g., env vars or profile-driven config).
- **FR-004**: On startup, the application MUST establish a connection to the
  configured database and either reach a running state or exit with a clear,
  diagnosable error within a bounded time.
- **FR-005**: The application MUST expose a single HTTP health endpoint that
  reports liveness of the application process and the reachability of its
  database.
- **FR-006**: The health endpoint MUST return a success status when the
  application is running and the database is reachable, and a non-success
  status when the database is unreachable.
- **FR-007**: The application MUST log datasource connection outcomes
  (success or failure) at a level that is visible by default, so developers
  can confirm wiring without changing log levels.
- **FR-008**: `run.md` MUST document, from the perspective of a developer
  who has just cloned the repository: prerequisites, how to start the
  database, how to build, how to run the application, how to run the tests,
  and how to hit the health endpoint. Every command in `run.md` MUST work
  end-to-end on a clean machine.
- **FR-009**: The repository MUST contain at least one automated test
  artifact that exercises the application's startup path or its health
  endpoint, so that the foundation can be verified without manual steps.
- **FR-010**: The application MUST refuse to start in a half-configured
  state — if a required configuration value (database URL, credentials) is
  missing, startup MUST fail with a message naming the missing value rather
  than silently defaulting to something inappropriate.

### Key Entities

This feature does not introduce business entities. The "entities" at this
layer are operational components:

- **Application Process**: The running server. Its lifecycle is "starting →
  ready → stopping". Verifiable via process state and the health endpoint.
- **Database Instance**: A running database that the application points at.
  Its lifecycle is "starting → accepting connections → stopping". Verifiable
  via connection success from the application.
- **Health Report**: The payload returned by the health endpoint. It
  identifies, at minimum, the application's overall state and the
  database's reachability.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer who has never seen this repository can clone it,
  follow `run.md`, and reach a running application with a healthy database
  connection in under 15 minutes on a machine that already has the
  documented prerequisites installed.
- **SC-002**: From a fresh clone, the documented build command succeeds on
  the first attempt for 100% of developers who have the prerequisites
  installed (no "fix this one thing first" follow-up steps).
- **SC-003**: From a fresh clone, the documented run command brings the
  application to a healthy state within 30 seconds of the database being
  ready.
- **SC-004**: The health endpoint returns a correct status (success when
  healthy, non-success when the database is unreachable) in 100% of the
  tested scenarios.
- **SC-005**: When startup fails (wrong Java version, database not running,
  missing config), the developer can identify the root cause from the
  surfaced error message alone in at least 9 of 10 cases — without reading
  source code.
- **SC-006**: The automated test that exercises startup or the health
  endpoint runs in under 30 seconds and passes on a clean machine.

## Assumptions

- The "database" referenced by the user is PostgreSQL, consistent with the
  `pom.xml` driver, the README's mention of `compose.yml`, and standard
  practice for this project's stack. An in-memory database (already present
  in dependencies) is acceptable for the automated startup test, but the
  documented run path targets PostgreSQL.
- Local database provisioning is provided by the `compose.yml` referenced
  in the README; developers are assumed to have a container runtime (e.g.,
  Docker) available.
- The health endpoint is exposed at a stable, conventional path (e.g.,
  `/health`) and requires no authentication at this stage. Authentication,
  authorization, and rate limiting on operational endpoints are out of
  scope for the skeleton and will be addressed by a later feature.
- "Nothing useful happens yet" is interpreted to mean no business
  endpoints (no `/users`, `/tickets`, etc.). The health endpoint is treated
  as plumbing, not business functionality, and is therefore in scope.
- CI configuration (GitHub Actions, etc.) is out of scope for this feature;
  `run.md` is the source of truth for "how to verify the skeleton works"
  and CI can wrap those same commands in a later feature.
- The choice of which specific health-reporting library or convention to
  use is an implementation detail and belongs in the plan, not the spec.
- The skeleton targets a single deployment target (locally runnable JVM
  application + locally runnable database). Production deployment topology
  (clustering, managed databases, secrets management) is out of scope.

## Out of Scope

- Any business domain feature (users, projects, tickets, comments, audit
  logs, attachments, mentions, scheduling, import/export). These are
  separate features that build on top of this skeleton.
- Authentication and authorization of any endpoint, including the health
  endpoint.
- Production-grade observability (metrics export, distributed tracing,
  structured log shipping). The skeleton's logging requirement is bounded
  to "you can see datasource success/failure on startup."
- CI/CD pipeline definition.
- Database schema, migrations, and seed data — the skeleton requires that
  the application connect to a database, not that the database have a
  schema.
