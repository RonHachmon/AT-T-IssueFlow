# AI Prompts Log

Worked through Spec-Driven Development (SDD) using Spec Kit. The goal was to make the long-running architectural decisions for the entire application up front, before any implementation began, so that every later phase could be built against a stable, agreed-upon foundation instead of being redesigned on the fly. Claude broke the project down into distinct, sequential phases, and each phase was then implemented through Spec Kit, which kept the model grounded to its assigned task and to the principles laid out in the spec.
Most of the plan and prompt can be viewed under the specs director and .specify  directory.

---

## Models Used

| Role | Model |
|------|-------|
| Planning development phases | **Claude Opus** |
| Executing the plan | **Claude Sonnet** |
| Commit notes through git commit skill | **Claude Haiku** |



---
# IssueFlow Constitution

## Core Principles

### I. Clean Code

Code in this repository follows Robert C. Martin's *Clean Code* as the
authoritative reference. The following rules are non-negotiable:

- **Names reveal intent.** No abbreviations, no single-letter identifiers
  outside of short loop counters, no encoded type prefixes.
- **Functions are short and do one thing.** A function operates at a single
  level of abstraction; mixing high-level orchestration with low-level
  string/SQL manipulation in the same method is a defect.
- **No flag arguments.** A boolean parameter that switches behavior MUST be
  split into two methods with intention-revealing names.
- **No dead or commented-out code.** Delete it. Git remembers.
- **No magic numbers or magic strings.** Extract to named constants or enums.
- **Comments explain WHY, not WHAT.** If a comment restates the code, the code
  needs a better name, not a comment. Comments are reserved for non-obvious
  constraints, workarounds, and invariants.

**Rationale**: Maintainability compounds. Every "broken window" left in place
licenses the next one. These rules are cheap to follow up front and ruinously
expensive to retrofit.

### II. Testing Standards

Every feature ships with BOTH layers of tests; neither layer alone is
sufficient:

- **Code-level unit tests** using **JUnit 5 + Mockito**, with **no Spring
  context** (no `@SpringBootTest`, no `@DataJpaTest`). Unit tests run on the
  pure JVM, instantiate the class under test directly, and mock collaborators.
- **Postman API tests** stored under `postman/` and runnable via Newman, that
  exercise the HTTP contract end-to-end.

Coverage rules:

- **One or two sanity tests per feature are MANDATORY.** A feature without a
  smoke test does not ship.
- **Complex tests are MANDATORY wherever any of the following exists**:
  branching logic, state transitions, concurrency or scheduling, or edge cases
  (empty inputs, boundary values, conflicts, race windows, idempotency).
- **Coverage target is small-to-medium.** Cover critical paths and tricky
  logic. Do NOT write tests for trivial getters, setters, DTO mappers, or
  generated code; they add maintenance cost without adding signal.

Test style:

- Test names read as sentences describing behavior, e.g.
  `returnsConflictWhenIssueIsAlreadyClosed`. No `test1`, no
  `testCreateUser_success`.
- Tests follow **Arrange-Act-Assert** with clear blank-line separation between
  the three blocks.

**Rationale**: Pure-JVM unit tests are fast and force good design (dependencies
become explicit collaborators). Postman covers the wire contract. Trivial
tests dilute the suite's signal-to-noise ratio and slow CI without catching
real bugs.

### III. Documentation Discipline

- **Javadoc on public methods only.** Every public method MUST document its
  purpose, every `@param`, the `@return` value (or note `void`), and every
  exception it can throw (`@throws`). Private methods rely on clean naming
  (Principle I) and do NOT get Javadoc.
- **README endpoint coverage.** Every API endpoint MUST appear in `README.md`
  with verb, path, request body shape, response status, and response body
  shape. Adding or changing an endpoint without updating the README is a
  defect.
- **`run.md` MUST always work from a fresh clone.** Every step (install,
  database start, build, run, test) is verified before merge whenever it
  changes. A `run.md` that fails on a clean machine is treated as a broken
  build.
- **`prompts.md` honestly records AI usage.** It names the model used and
  records the key prompts that shaped the code or specs. Do not embellish, do
  not omit.

**Rationale**: Documentation is the human-facing API surface. Rotten or
dishonest docs cost more than no docs at all, because they mislead.

### IV. API Consistency

The HTTP surface follows REST conventions strictly. Reviewers MUST cite this
principle when rejecting deviations.

- **README response templates are canonical.** Where `README.md` defines a
  specific response template for an endpoint (verb, path, status code, body
  shape), that template MUST be implemented exactly as written, even when it
  differs from the general conventions listed below. A README-defined template
  represents an accepted-by-design decision — do NOT "fix" it to match the
  generic rules. When no README template exists for an endpoint, the general
  conventions below apply.
- **HTTP verbs and status codes** *(default when README does not override)*:
  - `POST` that creates a resource -> `201 Created` with a `Location` header
  - `DELETE` and `PATCH`/`PUT` with no response body -> `204 No Content`
  - Missing resource -> `404 Not Found`
  - Conflict with existing state (duplicate, illegal transition) -> `409 Conflict`
  - Semantic validation failure -> `422 Unprocessable Entity`
  - Syntactic / type validation failure -> `400 Bad Request`
- **Resource paths use plural nouns**: `/users`, `/projects`, `/tickets`,
  `/tickets/{ticketId}/comments`.
- **Errors use RFC 7807 Problem Details** (`application/problem+json`) with an
  informative `title`, a human-readable `detail`, and a stable `type` URI per
  error class. No bare error strings, no inconsistent error envelopes.
- **JSON field naming is `camelCase`. SQL identifier naming is `snake_case`.**
  Mapping happens at the boundary; persistence naming MUST NOT leak into the
  API.
- **Standard field names**: primary key is `id`; foreign keys are
  `<entity>Id` (e.g., `projectId`, `assigneeId`); timestamps are `createdAt`
  and `updatedAt`.
- **DTOs at every controller boundary.** JPA entities MUST NOT appear in
  request or response bodies, ever. Map explicitly between entities and DTOs.
- **Pagination has one canonical shape** across all endpoints (e.g.,
  `{ "data": [...], "page": N, "pageSize": N, "total": N }`). New endpoints
  MUST adopt the same envelope rather than inventing new ones.
- **Prefer popular, well-maintained libraries** over bespoke code when one
  exists for the job: Bean Validation (`jakarta.validation`) for input
  validation, MapStruct for entity/DTO mapping, springdoc-openapi for OpenAPI
  generation, Zalando Problem (or equivalent) for RFC 7807, Spring Data for
  pagination. Hand-rolled equivalents require justification in the plan's
  Complexity Tracking section.

**Rationale**: Consistency is the only thing that makes a REST API
discoverable. Once a client has learned one endpoint, they should be able to
predict the rest. The README tables represent the agreed external contract;
following them exactly — even when they use non-standard status codes — ensures
the implementation matches the specification clients were given.

## Technology Standards

- **Runtime**: Java 21+ on Spring Boot 3.x (per `pom.xml`).
- **Build**: Maven, single `pom.xml` at the project root, Maven wrapper
  (`mvnw`) used in all documentation.
- **Persistence**: PostgreSQL via Spring Data JPA in production; H2 acceptable
  for tests that genuinely need a SQL backend (rare — most unit tests are
  pure JVM per Principle II).
- **Testing libraries**: JUnit 5 (`junit-jupiter`), Mockito, AssertJ. Spring
  context is permitted ONLY for narrowly-scoped integration tests; the
  default test is pure JVM.
- **API tests**: Postman collections under `postman/`, executable via Newman
  in CI.
- **Code generation / boilerplate**: Lombok and MapStruct are acceptable where
  they reduce noise without hiding intent. Avoid niche, unmaintained, or
  single-author libraries.

## Development Workflow

Every pull request MUST:

1. Pass `./mvnw verify` (compile, unit tests). Newman-based Postman runs
   execute against any new or changed endpoints.
2. Include both a unit test and a Postman test for any new branching logic,
   state transition, concurrent code path, or non-trivial edge case
   (Principle II).
3. Update `README.md` if endpoints were added, removed, or changed
   (Principle III).
4. Update `run.md` if startup, build, or test invocation changed
   (Principle III).
5. Add a `prompts.md` entry if AI assistance materially shaped the change
   (Principle III).
6. Include a short "Constitution Check" in the PR description either
   attesting compliance or listing accepted deviations with justification.

Code review:

- Reviewers MUST cite the violated principle (e.g., "Principle I: flag
  argument") when requesting changes. Vague review comments are themselves
  rejected.
- Any deviation from a principle MUST be justified in the plan's Complexity
  Tracking section before review begins.

## Governance

This constitution supersedes all other practice documents in this repository.
When a doc conflicts with the constitution, the constitution wins until the
constitution is amended.

**Amendment procedure**: Open a PR modifying `.specify/memory/constitution.md`.
The PR MUST update the version line, the last-amended date, the Sync Impact
Report at the top of this file, and any dependent templates flagged in that
report.

**Versioning policy** (semantic):

- **MAJOR**: backward-incompatible removal or redefinition of a principle, or
  removal of a Governance rule.
- **MINOR**: a new principle or section is added, or an existing principle is
  materially expanded.
- **PATCH**: clarifications, wording, typos, or non-semantic refinements.

If a version bump type is ambiguous, the PR author MUST state the reasoning in
the PR description.

**Compliance review**: Every PR description includes the "Constitution Check"
line item above. The `/speckit-plan` and `/speckit-tasks` commands MUST also
re-validate against this file (see plan-template Constitution Check gates).

Runtime development guidance lives in `CLAUDE.md` and `run.md`; both MUST stay
aligned with this constitution.
---

## High-Level Plan

The project was broken down into the following phases.

---

### Phase 0 — Project Skeleton & Database

**Non-technical:** Set up the empty project so it compiles, connects to a database, and runs. Nothing useful happens yet, but the foundation is in place: a running server, a running database, and the plumbing between them. Think of it as building the empty house (walls, plumbing, electricity) before any furniture goes in.

**Technical:** Spring Boot 3.4 + Java 21 project initialized via Spring Initializr or the provided skeleton, built with Maven (`mvnw`). Core dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `postgresql` driver, `lombok`, and `spring-boot-starter-test` with JUnit 5 and Mockito. PostgreSQL via the provided `compose.yml`.

Use **Flyway** (`flyway-core` + `flyway-database-postgresql`) for schema migrations rather than `ddl-auto=update`; migrations are reviewable and version-controlled. Configure `application.yml` with datasource properties and Hibernate dialect.

Add a global exception handler scaffold (`@RestControllerAdvice`) returning RFC 7807 `ProblemDetail` responses, ready to be extended in later phases.

**Project structure:** package-by-feature (`user`, `project`, `ticket`, `comment`, `auth`, `common`) rather than package-by-layer.

---

### Phase 1 — User Management

**Non-technical:** Add the concept of a person to the system. You can create users, fetch them, list them all, update their name or role, and delete them. Each user has a username, email, full name, and a role that is either `ADMIN` or `DEVELOPER`. No login yet; this phase just establishes who exists in the system.

**Technical:**

- `User` JPA entity with `@Entity`, UUID or auto-increment `Long` id (pick one and use it consistently across all entities). UUIDs are easier with distributed systems but `Long` is simpler; **recommend `Long` here**.
- Role as a Java enum stored via `@Enumerated(EnumType.STRING)`.
- Spring Data JPA `UserRepository extends JpaRepository<User, Long>`.
- DTOs for request/response (`CreateUserRequest`, `UpdateUserRequest`, `UserResponse`); never expose entities directly.
- Use **MapStruct** (`mapstruct` + `mapstruct-processor`) for entity↔DTO mapping. It's compile-time generated, fast, and removes hand-written boilerplate.
- Validation via `jakarta.validation` annotations (`@NotBlank`, `@Email`, `@Size`) on DTOs, triggered with `@Valid` on controller methods.
- **Password field:** add a `passwordHash` column now (even though login comes in Phase 2) so you don't migrate the table later. Store with BCrypt via `BCryptPasswordEncoder` from `spring-security-crypto`.
- Unique constraints on `username` and `email` at both the JPA level (`@Column(unique = true)`) and via Flyway migration.

**Unit tests:** service-layer tests with Mockito mocking the repository, covering validation rules, duplicate username/email rejection, and role enum constraints.

---

### Phase 2 — Authentication (Stateless JWT)

**Non-technical:** Lock the system down. From this phase forward, every endpoint except login requires the caller to first log in with a username and password, receive a token, and send that token with every subsequent request. Tokens expire after 12 minutes, so if someone steals a token, the damage window is small. There's a "who am I" endpoint that returns the current user. Logout is a no-op for now since tokens are stateless; proper logout is addressed in the final phase.

**Technical:**

- Add `spring-boot-starter-security` and `jjwt-api` + `jjwt-impl` + `jjwt-jackson` (the `io.jsonwebtoken` library) for JWT signing/verification using HS256 with a secret in `application.yml`.
- Configure `SecurityFilterChain` as a `@Bean` (the modern, non-deprecated approach; `WebSecurityConfigurerAdapter` is removed in Spring Security 6): stateless session policy, CSRF disabled (REST API), `/auth/login` permitted, all else authenticated.
- Implement a `JwtAuthenticationFilter extends OncePerRequestFilter` that extracts the `Authorization: Bearer` header, validates the token, and populates `SecurityContextHolder`.
- **Token claims:** `sub` (username), `userId`, `role`, `iat`, `exp`. Expiry configurable via `app.security.jwt.access-token-ttl=PT12M`.
- `UserDetailsService` implementation loading from `UserRepository`. `BCryptPasswordEncoder` bean for password verification.
- `/auth/logout` returns 204 and does nothing (documented as such; real revocation comes in the final phase).
- `/auth/me` reads from `SecurityContextHolder` and returns the current user's profile.
- Use `@PreAuthorize("hasRole('ADMIN')")` for role-based authorization later. Enable it now with `@EnableMethodSecurity`.

**Unit tests:** filter logic with mocked JWT parsing, login service rejecting bad credentials, token generation producing valid claims.

---

### Phase 3 — Project Management

**Non-technical:** Introduce projects, the containers that will later hold tickets. You can create a project with a name, description, and owner (a user). You can fetch one, list them all, update name/description, and delete one. Deleting is "soft": the project disappears from normal listings but isn't truly gone, so it can be restored later. The restore endpoints themselves come in a dedicated phase.

**Technical:**

- `Project` JPA entity with `@ManyToOne` relationship to `User` for owner (lazy fetch). Foreign key constraint via Flyway migration.
- **Soft delete baked in from the start:**
  - `deletedAt` (nullable `Instant`) column
  - `@SQLDelete(sql = "UPDATE projects SET deleted_at = NOW() WHERE id = ?")` to override Hibernate's delete behavior
  - `@SQLRestriction("deleted_at IS NULL")` (Hibernate 6+, replaces the deprecated `@Where`) so all queries automatically filter out soft-deleted rows
- This means standard `findAll`, `findById`, etc. transparently ignore deleted records (no per-query filtering needed). Admin endpoints for listing/restoring deleted projects come in Phase 9 and will bypass the restriction via native queries or a dedicated method annotated to disable the filter.
- Repository, service, controller, DTOs, MapStruct mapper following the Phase 1 pattern.
- Validate owner exists during creation (404 if not).

**Unit tests:** soft-delete behavior (deleting then fetching returns empty), owner validation, name/description update rules.

---

### Phase 4 — Ticket Management

**Non-technical:** Add the actual work items. Each ticket belongs to one project and has a title, description, status (`TODO` → `IN_PROGRESS` → `IN_REVIEW` → `DONE`), priority (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`), type (`BUG`/`FEATURE`/`TECHNICAL`), and optionally an assigned developer. The lifecycle is strict: tickets can only move forward through statuses, never backward. Once `DONE`, a ticket is frozen with no further edits. When two people try to update the same ticket simultaneously, one of them gets a clear conflict error rather than silently overwriting the other's changes. Tickets are soft-deleted just like projects.

**Technical:**

- `Ticket` entity with `@ManyToOne` to `Project` and nullable `@ManyToOne` to `User` (assignee), enums for `Status`, `Priority`, `Type` stored as strings.
- **Optimistic locking:** `@Version` annotation on a `Long version` field. JPA automatically increments it on each update and throws `OptimisticLockException` if the version in the request doesn't match the DB. Wrap this in `@RestControllerAdvice` to return HTTP 409 with a clear `ProblemDetail`.
- Clients send the current version in update requests (either as a field or `If-Match` header; **recommend field-in-body for simplicity**).
- **State transition rules** enforced in the service layer with a small state machine: a `Map<Status, Set<Status>>` of allowed transitions, throwing an `IllegalStateTransitionException` (→ HTTP 422) otherwise.
- "Cannot edit `DONE`" enforced as a guard at the top of the update service method.
- Soft delete via `@SQLDelete` + `@SQLRestriction` as in Phase 3.
- **PATCH semantics for updates:** use a DTO with `Optional<T>` fields or a `JsonNullable<T>` (from `openapi-jackson-databind-nullable`) to distinguish "field not present" from "field explicitly set to null".
- Validate `projectId` exists and is not soft-deleted on creation.

**Unit tests** should be thorough here: every valid transition, every invalid transition, `DONE`-is-frozen rule, optimistic lock conflict, assignee-must-exist, project-must-exist.

---

### Phase 5 — Comment Management

**Non-technical:** Let users discuss tickets. Anyone can post a comment on a ticket, see all comments on a ticket, edit a comment, or delete one. Like tickets, comments have the same protection against two people editing the same comment at the same time.

**Technical:**

- `Comment` entity with `@ManyToOne` to `Ticket` (the parent) and `@ManyToOne` to `User` (author).
- `content` as `@Column(columnDefinition = "TEXT")`.
- `createdAt` and `updatedAt` populated via JPA auditing (`@CreatedDate`, `@LastModifiedDate` with `@EnableJpaAuditing` on the main config class).
- `@Version` field for optimistic locking, same pattern as tickets.
- Comments do **NOT** need soft delete per the spec; use real deletes.
- Validate that the ticket exists (and isn't soft-deleted; this is automatic thanks to `@SQLRestriction` on `Ticket`) and that the author exists.
- Endpoint `GET /tickets/{id}/comments` returns the list ordered by `createdAt ASC`.

**Unit tests:** optimistic lock conflict, author/ticket existence validation, content-not-blank validation, ordering.

---

### Phase 6 — Audit Log

**Non-technical:** Record everything that changes. Every time a user (or the system itself, in later phases) creates, updates, or deletes something, a permanent record is written: who did it, when, what kind of action, on what kind of thing, and which specific record. These logs can never be edited or deleted, only read. There's an endpoint to list logs, with the ability to filter by who acted, what kind of action it was, and when it happened.

**Technical:**

- `AuditLog` entity with fields: `id`, `actor` (username or `"SYSTEM"`), `actorUserId` (nullable, for SYSTEM actions), `action` (enum: `CREATE`, `UPDATE`, `DELETE`, `SOFT_DELETE`, `RESTORE`, `STATUS_CHANGE`, plus `AUTO_ASSIGN` and `AUTO_ESCALATE` added in their respective phases), `entityType` (string: `USER`, `PROJECT`, `TICKET`, `COMMENT`, etc.), `entityId`, `timestamp`, `details` (JSONB column for old/new values; map to `Map<String, Object>` with Hibernate's `JsonType` from `hibernate-types-60` or use Spring's built-in JSONB support in Hibernate 6.x via `@JdbcTypeCode(SqlTypes.JSON)`).
- **Implementation approach:** Use Spring AOP with a custom `@Auditable` annotation, OR (cleaner and more reliable) use JPA entity listeners (`@EntityListeners`) with `@PostPersist`, `@PostUpdate`, `@PostRemove` callbacks on auditable entities. **Recommend the listener approach:** it can't be bypassed by direct repository calls and runs in the same transaction.
- The listener needs access to the current user. Inject `SecurityContextHolder` lookups via a static utility or a `@Component` accessed through a `BeanUtil` (entity listeners aren't Spring-managed by default; configure with `SpringBeanAutowiringInterceptor` or use a static holder).
- `GET /audit-logs` with query params `?actor=&action=&entityType=&from=&to=` filtered via JPA Specifications (`spring-data-jpa`'s `JpaSpecificationExecutor`); composable, type-safe filtering without writing one method per filter combination.
- **No PUT/PATCH/DELETE endpoints.** Append-only enforced at the API level (no controller methods exist) and the DB level (no GRANTs needed since the app is the only writer, but you can add a comment in the migration).

**Unit tests:** filter combinations, entity listener firing on save/update/delete, SYSTEM actor handling.

---

### Phase 7 — Ticket Dependencies

**Non-technical:** Let tickets block other tickets. If ticket A is blocked by ticket B, then A can't be marked `DONE` until B is done. You can add a blocker, list a ticket's blockers, and remove a blocker. Blockers can only be other tickets in the same project.

**Technical:**

- Many-to-many self-relationship on `Ticket`. Model it as an **explicit join entity** `TicketDependency` with `blockedTicketId` and `blockerTicketId` plus a composite unique constraint; explicit join entities are easier to query and audit than `@ManyToMany`.
- Endpoint logic validates: both tickets exist, both belong to the same project, no self-dependency (`blockedTicketId != blockerTicketId`), and no cycle introduction.
- **Cycle detection:** before inserting, run a DFS from `blockerTicketId` checking that `blockedTicketId` is not reachable. A recursive CTE in PostgreSQL works too and is faster for deep graphs (`WITH RECURSIVE` query), but a Java-side DFS is fine for the expected data sizes.
- Modify the ticket status-transition logic from Phase 4: when transitioning to `DONE`, query for any unresolved blockers (`SELECT ... WHERE blocked_ticket_id = ? AND blocker.status != 'DONE'`) and reject with HTTP 422 if any exist.

**Unit tests:** cross-project blocker rejected, self-dependency rejected, cycle rejected, `DONE` blocked by open blocker, `DONE` allowed when all blockers `DONE`.

---

### Phase 8 — Attachment Management

**Non-technical:** Let users upload files to tickets: screenshots, PDFs, logs, plain text. Each file must be 10 MB or smaller, and only certain file types are allowed (PNG, JPEG, PDF, plain text). Anything else is rejected with a clear error.

**Technical:**

- `Attachment` entity with `id`, `ticketId`, `filename`, `contentType`, `sizeBytes`, `storageKey` (the path/identifier in whatever storage backend is used), `uploadedBy`, `uploadedAt`.
- **Strategy pattern for storage:** define an interface:

```java
public interface FileStorageStrategy {
    String store(InputStream content, String filename, String contentType);
    InputStream retrieve(String storageKey);
    void delete(String storageKey);
}
```

with a single `LocalFileSystemStorageStrategy` implementation writing under a configured directory (`app.storage.local.base-path`). Future S3 or DB-blob implementations are then drop-in replacements behind the same interface. Wire via `@ConditionalOnProperty` so the active strategy is chosen via configuration.

- Upload endpoint accepts `multipart/form-data` using Spring's `MultipartFile`.
- **Enforce limits at two layers:**
  1. Spring's `spring.servlet.multipart.max-file-size=10MB` and `max-request-size=10MB` (returns 413 at the framework level before your code runs)
  2. An explicit service-layer check on `MultipartFile.getSize()` as defense-in-depth
- **Content type whitelist** enforced by checking `MultipartFile.getContentType()` against a `Set<String>` AND validating actual content using **Apache Tika** (`tika-core`); clients can lie about `Content-Type`, so sniff the real type from the bytes. Reject mismatches with HTTP 415.
- Generate storage keys with `UUID.randomUUID()` to avoid filename collisions and path traversal.

**Unit tests:** oversized file rejected, wrong MIME type rejected, MIME spoofing rejected (Tika check), happy path stores and retrieves.

---

### Phase 9 — Soft Delete Admin Endpoints

**Non-technical:** Soft delete itself has been working since Phase 3 (projects) and Phase 4 (tickets); deleted records are already hidden from normal views. This phase adds the admin-facing pieces: admins can list what's been deleted, and they can restore deleted items back to active status. Non-admins can't use these endpoints at all.

**Technical:**

- Four endpoints, all guarded by `@PreAuthorize("hasRole('ADMIN')")`:
  - `GET /projects/deleted`
  - `GET /tickets/deleted?projectId=`
  - `POST /projects/{id}/restore`
  - `POST /tickets/{id}/restore`
- Listing soft-deleted records requires **bypassing `@SQLRestriction`**. Options:
  - (a) use `@QueryHints` with Hibernate's filter disable
  - (b) write a native query in the repository
  - (c) define a dedicated repository method using `@Query(value = "SELECT ... WHERE deleted_at IS NOT NULL", nativeQuery = true)`
- **Recommend (c):** explicit, no global side effects.
- Restore is an `UPDATE ... SET deleted_at = NULL` via a `@Modifying @Query` method.
- Both list and restore operations write audit log entries (action `RESTORE`).
- **When restoring a project, decide policy:** does it auto-restore its tickets? Spec is silent. **Recommend NO**, restore each independently, and document this.

**Unit tests:** non-admin rejected (403), restore clears `deletedAt`, restored records re-appear in normal queries, audit log entry written.

---

### Phase 10 — @Mention Mechanism in Comments

**Non-technical:** When a user writes `@alice` in a comment, the system recognizes that alice was mentioned, links the mention to alice's user record, and makes it possible to look up every comment where alice has ever been mentioned. If the comment is later edited to remove the mention or add a new one, the mentions update accordingly. Matching usernames is case-insensitive.

**Technical:**

- New `CommentMention` join entity: `id`, `commentId`, `mentionedUserId`, unique on `(commentId, mentionedUserId)`.
- On comment create/update, parse the body with regex `@(\w+)` (or stricter pattern matching your username rules), look up each username case-insensitively (`UserRepository.findByUsernameIgnoreCase`), and reconcile mentions:
  - compute `newMentions \ oldMentions` for inserts
  - compute `oldMentions \ newMentions` for deletes
  - run both within the same transaction
- Unknown usernames are silently ignored (don't fail the comment).
- Extend the comment response DTO to include `mentionedUsers: [{ id, username, fullName }]`; populate via a separate query or an `@EntityGraph` fetch to avoid N+1.
- New endpoint `GET /users/{userId}/mentions` returns comments where that user was mentioned, ordered `createdAt DESC`, paginated with Spring's `Pageable`.
- **Notification side effect:** the spec says "the mentioned user is notified." Since there's no email/push system, interpret this as either:
  - an internal `Notification` record, OR
  - just rely on the `/users/{userId}/mentions` endpoint serving as the notification mechanism
- **Recommend the latter** to stay focused; document the choice.

**Unit tests:** mention parsed, case-insensitive matching, unknown username ignored, mention added on edit, mention removed on edit, response includes mention metadata.

---

### Phase 11 — Auto-Escalation of Overdue Tickets

**Non-technical:** Tickets can have an optional due date. A background task runs on a schedule; every time it runs, it looks at every ticket that's past its due date and not yet `DONE`, and bumps its priority up one level (`LOW` → `MEDIUM` → `HIGH` → `CRITICAL`). Once a ticket is `CRITICAL` and still overdue, instead of bumping further, it gets flagged as overdue, which appears in API responses. If a user manually changes a ticket's priority, the escalation state resets. Escalation never changes a ticket's status. For now the schedule is fixed in configuration; making it changeable at runtime comes in a later phase.

**Technical:**

- Add `dueDate` (`Instant`, nullable) and `isOverdue` (boolean, default false) columns to `Ticket`.
- **Background job:** `@Component` with a method annotated `@Scheduled(fixedDelayString = "${app.escalation.interval:PT5M}")`. Enable scheduling with `@EnableScheduling` on a config class. The job runs in a single transaction (or batches; start single).
- **Query:**

```sql
SELECT t FROM Ticket t
WHERE t.dueDate < CURRENT_TIMESTAMP
  AND t.status != 'DONE'
  AND t.deletedAt IS NULL
```

- For each result:
  - if `priority < CRITICAL`, bump it one level
  - if `priority == CRITICAL`, set `isOverdue = true`
- Write an audit log entry with `actor = SYSTEM`, `action = AUTO_ESCALATE`.
- **Optimistic lock interaction:** the scheduler will increment `@Version`. If a user is mid-update, one of them loses. **Recommend** the scheduler retries on `OptimisticLockException` (use Spring Retry: `spring-retry` + `@Retryable` with a small backoff) rather than failing silently.
- **Manual priority change** (in the ticket update service) clears `isOverdue = false`; next escalation cycle re-evaluates from the new priority. No special state needed since the cycle just runs the same logic.
- **Idempotency at `CRITICAL`** is automatic since the bump logic checks for `< CRITICAL`.
- Schedule defined via property `app.escalation.interval=PT5M` (ISO-8601 duration); Spring parses this directly.

**Unit tests:** `LOW→MEDIUM`, `HIGH→CRITICAL`, `CRITICAL→isOverdue` set, `CRITICAL→`no further change (idempotent), null due date ignored, `DONE` ticket ignored, manual priority change clears `isOverdue`.

---

### Phase 12 — Auto-Assignment by Workload

**Non-technical:** When someone creates a ticket without specifying who it's assigned to, the system picks the least-busy developer in that project and assigns it to them automatically. "Busy" means how many non-finished tickets that developer already has in this project. If there's a tie, the developer who registered first wins. If no developers exist in the project, the ticket stays unassigned without error. There's also a new endpoint that shows everyone's workload in a project, sorted by who's least busy. Every automatic assignment shows up in the audit log as a system action.

**Technical:**

The spec mentions "DEVELOPERs in the project," but the model so far has no project-user membership concept; only the project's owner. Two interpretations:

- **(a)** all `DEVELOPER`-role users are candidates for every project (simpler, matches a literal reading)
- **(b)** introduce a `ProjectMember` join entity

**Recommend (a)** since the spec never describes adding members to projects elsewhere; flag this for your decision.

Going with (a): in the ticket creation service, when `assigneeId` is absent, run a query like:

```sql
SELECT u.* FROM users u
LEFT JOIN tickets t
  ON t.assignee_id = u.id
  AND t.project_id = :projectId
  AND t.status != 'DONE'
  AND t.deleted_at IS NULL
WHERE u.role = 'DEVELOPER'
GROUP BY u.id
ORDER BY COUNT(t.id) ASC, u.created_at ASC
LIMIT 1
```

Implement either as a JPQL query with a projection or as a native query.

- Add `createdAt` to `User` if it isn't there yet (via JPA auditing, retroactive Flyway migration).
- New endpoint `GET /projects/{projectId}/workload` returns `[{ userId, username, openTicketCount }]` sorted ascending; same query without the `LIMIT`, returning all developers.
- Write audit log entry with `actor = SYSTEM`, `action = AUTO_ASSIGN`.
- Per spec, **auto-assignment only fires on creation, never on update**.

**Unit tests:** assigned to least-loaded developer, tie broken by registration order, no developers → null assignee no error, `ADMIN` excluded, explicit `assigneeId` bypasses auto-assignment, update never triggers auto-assignment.

---

### Phase 13 — Ticket Export & Import (CSV)

**Non-technical:** Admins can download all tickets in a project as a CSV file, and they can upload a CSV file to create many tickets at once. The CSV must correctly handle tricky data like commas, quotes, and newlines inside ticket descriptions. The import returns a summary showing how many tickets were created, how many failed, and what went wrong for the failed ones.

**Technical:**

- Use **OpenCSV** (`com.opencsv:opencsv`) or **Apache Commons CSV** (`commons-csv`); both handle RFC 4180 escaping (quoting, embedded commas/quotes/newlines) correctly. **Recommend Apache Commons CSV:** simpler API, well-maintained.
- **Export:** `GET /tickets/export?projectId={id}` returns `Content-Type: text/csv` with `Content-Disposition: attachment; filename="tickets-{projectId}.csv"`. Stream the response using `StreamingResponseBody` to avoid loading all tickets into memory for large projects.
  - Header row: `id,title,description,status,priority,type,assigneeId`
- **Import:** `POST /tickets/import` accepts `multipart/form-data` with `file` (the CSV) and `projectId` (form field). Parse row-by-row; for each row, validate fields and call the existing ticket creation service (which already applies auto-assignment if `assigneeId` is empty; nice composition).
- Collect successes and per-row failures rather than aborting on the first error.
- **Response:**

```json
{
  "created": 42,
  "failed": 3,
  "errors": [
    { "row": 5, "message": "..." }
  ]
}
```

- **Transaction strategy:** wrap the whole import in a single transaction, or per-row independent transactions?
  - Per-row (partial success allowed) is more user-friendly
  - All-or-nothing is safer
  - Spec language ("`created: 42, failed: 3`") implies partial success; go with **per-row transactions** using `TransactionTemplate` programmatically inside the loop.

**Unit tests:** roundtrip (export then import produces same tickets), commas/quotes/newlines escaped correctly, invalid row reported but doesn't abort, oversized file rejected (reuse Phase 8 multipart limits).
