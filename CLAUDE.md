# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common commands

The Maven wrapper is checked in — never assume a local `mvn` install. On Windows shells (PowerShell / `cmd`), substitute `.\mvnw.cmd` for `./mvnw`.

```bash
./mvnw spring-boot:run                              # run the API on :8080 (requires Postgres up)
./mvnw clean verify                                 # full build: Spotless check + compile + unit tests
./mvnw test                                         # unit tests only
./mvnw test -Dtest=CommentServiceTest               # single test class
./mvnw test -Dtest=CommentServiceTest#methodName    # single test method
./mvnw spotless:apply                               # auto-format to Google Java Format
./mvnw package                                      # build the runnable jar in target/
docker compose up -d                                # start Postgres (:5432) + Adminer (:8081)
npx newman run postman/<collection>.json -e postman/issueflow.local.postman_environment.json
```

Spotless runs in the `compile` phase — a formatting drift breaks the build, not just `verify`. Run `spotless:apply` before pushing.

A working JWT secret is required to boot: `APP_SECURITY_JWT_SECRET` must be ≥32 chars (the default `change-me-in-production-use-32-plus-chars` is acceptable for local dev only). See [run.md](run.md) for full bootstrap.

## Architecture

### Feature-slice packaging

Code is organised by feature, not by technical layer. Each slice under [src/main/java/com/att/tdp/issueflow/](src/main/java/com/att/tdp/issueflow/) (`user/`, `auth/`, `project/`, `ticket/`, `comment/`) contains its own `Controller`, `Service`, `Repository`, `Mapper`, entity, and `dto/` sub-package. Cross-cutting concerns live in [common/](src/main/java/com/att/tdp/issueflow/common/) (`error/`, `security/`, `health/`, `pagination/`, `config/`).

When adding a feature, mirror the existing layout one-for-one — the `comment` slice was built as an exact structural clone of `ticket`.

### Request flow and boundaries

`Controller` → `Service` → `Repository`. The boundaries are strict:

- **Controllers** only trigger validation (`@Valid`) and delegate. They never catch exceptions to map them to status codes.
- **Services** hold business logic, run inside `@Transactional`, and throw *semantic* exceptions (`NotFoundException`, `DuplicateResourceException`, `ForbiddenException`, `InvalidStateTransitionException`, `StaleVersionException`) — never HTTP types.
- **Repositories** are Spring Data JPA interfaces.
- **JPA entities never appear in request/response bodies.** Mapping is explicit via MapStruct (`*Mapper` with `componentModel=spring`, `unmappedTargetPolicy=ERROR`).

### Error handling — RFC 7807 only

[GlobalExceptionHandler](src/main/java/com/att/tdp/issueflow/common/error/GlobalExceptionHandler.java) is the single mapping point from exceptions to HTTP responses. All responses use `application/problem+json`, built via [ProblemDetailFactory](src/main/java/com/att/tdp/issueflow/common/error/ProblemDetailFactory.java) with stable `type` URIs from [ErrorType](src/main/java/com/att/tdp/issueflow/common/error/ErrorType.java). The 401 entry point in [SecurityConfiguration](src/main/java/com/att/tdp/issueflow/common/security/SecurityConfiguration.java) emits the same envelope for unauthenticated requests so the contract is uniform.

When adding new domain errors: add an `ErrorType` constant, a factory method, and a `@ExceptionHandler` mapping. Do not catch in the controller.

### Security model

Stateless JWT (HS256) via `jjwt`. The filter chain in `SecurityConfiguration` permits `/auth/login` and `/health/**`, requires auth for everything else, and runs [JwtAuthenticationFilter](src/main/java/com/att/tdp/issueflow/common/security/JwtAuthenticationFilter.java) before the standard form-login filter.

**Authorities use NO `ROLE_` prefix** — see [docs/decisions.md](docs/decisions.md) ADR-001. Use `hasAuthority('ADMIN')`, never `hasRole('ADMIN')` (the latter silently fails because it looks for `ROLE_ADMIN`).

Comment creation requires `request.authorId` to match the JWT subject's database id — see [CommentService.createComment](src/main/java/com/att/tdp/issueflow/comment/CommentService.java). Apply this pattern to any endpoint where a request body identifies an actor.

### Persistence patterns

- **Postgres in prod, in-memory H2 only for the rare test that needs JDBC** — most tests are pure JVM with Mockito and never touch a database.
- **Flyway** under [src/main/resources/db/migration/](src/main/resources/db/migration/) (`V<N>__<name>.sql`). `ddl-auto: none` — schema changes go through a new migration, never via Hibernate auto-generation.
- **Soft delete** is opt-in via a nullable `deleted_at` column. The canonical read is `findByIdAndDeletedAtIsNull(...)`; the canonical list is `findAllBy...AndDeletedAtIsNullOrderBy...`. There is no `deleted=false` filter — `NULL` is the active sentinel. Soft-deleted projects cascade-hide their tickets at the service layer (active project lookup precedes ticket query).
- **Optimistic locking** is enabled per-aggregate via `@Version`. JPA throws `ObjectOptimisticLockingFailureException`; the global advice maps it to `409 Conflict`. For client-driven version checks (comments), services throw `StaleVersionException` explicitly.
- **Timestamps**: tickets use `@PrePersist`/`@PreUpdate` lifecycle hooks; comments use Spring Data JPA auditing (`@EnableJpaAuditing` on the application class, `@CreatedDate`/`@LastModifiedDate` on the entity). Both mechanisms coexist intentionally — don't try to unify them in passing.

### Domain entities

| Entity | Table | Purpose | Lifecycle | Notable fields |
|---|---|---|---|---|
| [User](src/main/java/com/att/tdp/issueflow/user/User.java) | `users` | A person known to the system — author of comments, owner of projects, optional assignee of tickets. Holds the BCrypt `passwordHash` consumed by [IssueFlowUserDetailsService](src/main/java/com/att/tdp/issueflow/auth/IssueFlowUserDetailsService.java) during login. | Hard-deleted. `@PrePersist` / `@PreUpdate` timestamps. | `role` (`ADMIN` / `DEVELOPER`); case-insensitive uniqueness on `username` / `email` enforced via Flyway functional indexes on `LOWER(...)`, not the field-level `unique = true`. |
| [Role](src/main/java/com/att/tdp/issueflow/user/Role.java) | — (enum) | Authorisation tier carried on the `User` and emitted unchanged as the JWT `role` claim and the Spring Security authority string. | — | `ADMIN`, `DEVELOPER`. Exhaustive; adding a value is a feature, not a config tweak. |
| [Project](src/main/java/com/att/tdp/issueflow/project/Project.java) | `projects` | Top-level container that groups related tickets. Has exactly one owner (`User`). | Soft-deleted (`deletedAt`). A soft-deleted project cascades visibility — its tickets become invisible at the service layer. | `owner` (lazy `@ManyToOne` to `User`); `deletedAt` set only by the service, never by a hook. |
| [Ticket](src/main/java/com/att/tdp/issueflow/ticket/Ticket.java) | `tickets` | The core work item. Belongs to exactly one project; optionally assigned to a user. Status follows a forward-only state machine (see below). | Soft-deleted (`deletedAt`). `@PrePersist` / `@PreUpdate` timestamps. Optimistic lock via `@Version`. | `status` ([TicketStatus](src/main/java/com/att/tdp/issueflow/ticket/TicketStatus.java)), `priority` ([TicketPriority](src/main/java/com/att/tdp/issueflow/ticket/TicketPriority.java)), `type` ([TicketType](src/main/java/com/att/tdp/issueflow/ticket/TicketType.java)), `dueDate`. `project_id` is `updatable = false` — a ticket cannot change projects. |
| [Comment](src/main/java/com/att/tdp/issueflow/comment/Comment.java) | `comments` | A plain-text message left by a user on a ticket. | **Hard-deleted** (no `deletedAt`). Timestamps via Spring Data JPA auditing (`@CreatedDate` / `@LastModifiedDate`) — *not* `@PrePersist`. Optimistic lock via `@Version`. | `ticket_id` and `author_id` are both `updatable = false`. Author identity is enforced against the JWT subject at create time. |

Enum support types (no table, behaviour-bearing on the entity above):

- **`TicketStatus`** — `TODO → IN_PROGRESS → IN_REVIEW → DONE`. Ordinal-encoded; `isImmediateSuccessor` / `isTerminal` drive transition rules.
- **`TicketPriority`** — `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. Independent labels, no implicit ordering.
- **`TicketType`** — `BUG`, `FEATURE`, `TECHNICAL`. Classification only; no behavioural branching.

### Ticket state machine

`TicketStatus` is `TODO → IN_PROGRESS → IN_REVIEW → DONE` and is **forward-only by one step**. The enum's ordinal encodes the order; `isImmediateSuccessor` and `isTerminal` are the gating predicates in [TicketService.update](src/main/java/com/att/tdp/issueflow/ticket/TicketService.java). `DONE` tickets reject all updates — the whole record is frozen, not just the status field. If you add a status, you change the migration, the enum, and the transition checks together.

`UpdateTicketRequest.assigneeId` is `Optional<Long>` to distinguish three cases — field absent (no change), present-but-null (clear assignee), present-with-value (reassign). Preserve this triple-state pattern when adding nullable updatable foreign keys.

### Response shape conventions

README response templates in [README.md](README.md) are **canonical** per [.specify/memory/constitution.md](.specify/memory/constitution.md) Principle IV. Where the README says `200 OK` for a `POST` or `DELETE`, that overrides the otherwise-default `201`/`204`. Do not "fix" controllers to RESTful defaults — match the README table exactly.

Pagination uses a single envelope: `PagedResponse` (`{ data, page, pageSize, total }`). Reuse it for any new list endpoint that needs paging.

### Spec-Kit workflow

Features are planned under [specs/<NNN>-<slug>/](specs/) using the speckit skills (`speckit-specify`, `speckit-plan`, `speckit-tasks`, `speckit-implement`, `speckit-analyze`). Each feature has a `plan.md`, `tasks.md`, and (per the constitution) updates to README + run.md + prompts.md if applicable. The currently active plan is referenced in the SPECKIT block below — read it for feature-specific tech context before making non-trivial changes.

### Testing posture

Pure-JVM JUnit 5 + Mockito + AssertJ. Tests do **not** load a Spring context (no `@SpringBootTest`, no `@DataJpaTest`) — services are instantiated directly with mocked collaborators. Test names are sentences (`returnsConflictWhenIssueIsAlreadyClosed`), structured Arrange-Act-Assert with blank-line separation. Per the constitution, do not write tests for trivial getters/setters/mappers. Wire-level contracts are covered by the Postman collections under [postman/](postman/) (run with Newman).

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
[specs/006-ticket-comments/plan.md](specs/006-ticket-comments/plan.md)

Constitution (project rules):
[.specify/memory/constitution.md](.specify/memory/constitution.md)
<!-- SPECKIT END -->
