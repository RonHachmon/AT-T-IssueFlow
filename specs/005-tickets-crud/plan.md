# Implementation Plan: Tickets — Create, Read, Update, Transition, Soft-Delete

**Branch**: `005-tickets-crud` | **Date**: 2026-05-21 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/005-tickets-crud/spec.md`

## Summary

Add full CRUD for `Ticket` entities — the core work items belonging to projects. The system exposes five endpoints (`POST`, `GET` list, `GET` by ID, `PATCH`, `DELETE`) under `/tickets`. PATCH handles both field updates and status transitions; the service enforces a strict forward-only lifecycle (`TODO → IN_PROGRESS → IN_REVIEW → DONE`). DONE tickets are frozen — no field updates or transitions are accepted. Concurrent updates are protected by JPA optimistic locking; a conflicting write returns 409 Conflict. Deletion is soft: a `deleted_at` timestamp is set, and deleted tickets are invisible to all standard reads. All status codes follow the README Tickets APIs table (200 OK for all operations, matching the canonical template).

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.4.2  
**Primary Dependencies**: Spring Data JPA, Spring Web, Bean Validation (jakarta.validation), MapStruct, Lombok — all already in `pom.xml`; no new dependencies required  
**Storage**: PostgreSQL — new Flyway migration `V6__tickets.sql` adds the `tickets` table with `version` column for optimistic locking and a partial index on `(project_id, id) WHERE deleted_at IS NULL`  
**Testing**: JUnit 5 + Mockito (pure JVM, no Spring context); Postman collection updated with a new Tickets folder  
**Target Platform**: Linux container / local JVM (same as the rest of the service)  
**Project Type**: REST web service  
**Performance Goals**: Standard single-node Spring Boot response times; no special requirements for this feature  
**Constraints**: Forward-only status transitions; DONE tickets frozen; soft-delete only; no pagination (README shows plain array); no filter/sort query params in this phase; restore endpoints out of scope  
**Scale/Scope**: Single-node; no distributed concerns

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Gate | Pass? |
|---|------|-------|
| I  | **Clean Code** — `TicketService` has single-purpose methods (`create`, `getById`, `listByProject`, `update`, `softDelete`). State-transition guard is extracted to a private `validateTransition` method operating at a single abstraction level — no flag arguments. `InvalidStateTransitionException` carries a descriptive message string, not a boolean reason code. Named constants (`RESOURCE = "Ticket"`) replace magic strings. | [x] |
| II | **Testing Standards** — Pure-JVM `TicketServiceTest` covers: create happy path, create unknown project, create unknown assignee, getById happy, getById on soft-deleted, listByProject excludes deleted, update happy, update on DONE (frozen), backward transition, skip transition, concurrent update (optimistic lock), softDelete happy, softDelete idempotent. Postman: create (valid, missing title, unknown projectId, invalid priority), list by project, get by ID, update fields, forward transitions for each step, backward transition (409), DONE freeze (409), concurrent update (409), delete (valid, re-delete 404), no-auth (401). | [x] |
| III| **Documentation Discipline** — Javadoc on every public method in `TicketController`, `TicketService`, `TicketMapper`. README already contains the full Tickets APIs table — no update needed (the table is the canonical source). `run.md` unchanged (no new env vars). `prompts.md` entry added for this planning session. | [x] |
| IV | **API Consistency** — All five endpoints return `200 OK` per the README Tickets APIs table (the canonical template overrides the REST default of `201`/`204`). `GET /tickets?projectId=` returns a plain array per the README (overrides the project's `PagedResponse` convention). Response shape matches `{ id, title, description, status, priority, type, projectId, assigneeId, dueDate, isOverdue }`. Errors use RFC 7807 via existing `GlobalExceptionHandler` + `ProblemDetailFactory`. DTOs (`TicketResponse`, `CreateTicketRequest`, `UpdateTicketRequest`) at every controller boundary — no entity exposure. MapStruct `TicketMapper` handles all mapping including `isOverdue` computation. `ticketId` path variable follows `<entity>Id` convention. | [x] |

## Project Structure

### Documentation (this feature)

```text
specs/005-tickets-crud/
├── plan.md                      # This file
├── spec.md                      # Feature specification
├── research.md                  # Phase 0 decisions
├── data-model.md                # Phase 1 entity / migration design
├── quickstart.md                # Phase 1 smoke-test commands
├── contracts/
│   └── tickets.openapi.yaml     # Phase 1 OpenAPI contract
├── checklists/
│   └── requirements.md          # Spec quality checklist
└── tasks.md                     # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
pom.xml
  (no new dependencies — Spring Data JPA, MapStruct, Bean Validation, Lombok already present)

src/main/resources/db/migration/
└── V6__tickets.sql               # NEW — creates tickets table + partial index

src/main/java/com/att/tdp/issueflow/
├── common/error/
│   ├── ErrorType.java            # UNCHANGED
│   ├── InvalidStateTransitionException.java  # NEW — message-only RuntimeException
│   ├── GlobalExceptionHandler.java           # UPDATED — 2 new @ExceptionHandler methods
│   └── ProblemDetailFactory.java             # UNCHANGED — reuses existing conflict()
└── ticket/
    ├── package-info.java         # already exists (empty stub)
    ├── TicketStatus.java         # NEW — enum: TODO, IN_PROGRESS, IN_REVIEW, DONE
    ├── TicketPriority.java       # NEW — enum: LOW, MEDIUM, HIGH, CRITICAL
    ├── TicketType.java           # NEW — enum: BUG, FEATURE, TECHNICAL
    ├── Ticket.java               # NEW — @Entity with soft-delete + @Version + enum fields
    ├── TicketRepository.java     # NEW — JpaRepository + derived query methods
    ├── TicketService.java        # NEW — create / getById / listByProject / update / softDelete
    ├── TicketController.java     # NEW — 5 endpoints, all @ResponseStatus(OK)
    ├── TicketMapper.java         # NEW — MapStruct toEntity / toResponse (with isOverdue)
    └── dto/
        ├── CreateTicketRequest.java   # NEW
        ├── UpdateTicketRequest.java   # NEW
        └── TicketResponse.java        # NEW

src/test/java/com/att/tdp/issueflow/
└── ticket/
    └── TicketServiceTest.java         # NEW — 13 pure-JVM unit tests

postman/issueflow.postman_collection.json
  (updated — new "Tickets" folder with 14 requests)
```

## Implementation Notes

### `V6__tickets.sql` — Migration

```sql
CREATE TABLE tickets (
    id           BIGSERIAL       PRIMARY KEY,
    title        VARCHAR(255)    NOT NULL,
    description  TEXT,
    status       VARCHAR(20)     NOT NULL DEFAULT 'TODO',
    priority     VARCHAR(10)     NOT NULL,
    type         VARCHAR(15)     NOT NULL,
    project_id   BIGINT          NOT NULL REFERENCES projects(id),
    assignee_id  BIGINT          REFERENCES users(id),
    due_date     TIMESTAMPTZ,
    created_at   TIMESTAMPTZ     NOT NULL,
    updated_at   TIMESTAMPTZ     NOT NULL,
    deleted_at   TIMESTAMPTZ,
    version      BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX ix_tickets_project_active
    ON tickets (project_id, id)
    WHERE deleted_at IS NULL;
```

### `TicketStatus.java` — Enum

```java
public enum TicketStatus {
  TODO, IN_PROGRESS, IN_REVIEW, DONE;

  public boolean isTerminal() {
    return this == DONE;
  }

  public boolean isImmediateSuccessor(TicketStatus target) {
    return target.ordinal() == this.ordinal() + 1;
  }
}
```

Using `ordinal()` for sequencing avoids a separate field and is safe here because the enum is sealed and the sequence is fixed. Any future extension requires an explicit migration review.

### `Ticket.java` — Entity

- `@Version Long version` — JPA optimistic lock; never exposed in API responses
- `@Enumerated(EnumType.STRING)` on `status`, `priority`, `type`
- `@ManyToOne(fetch = FetchType.LAZY)` on `project` (not updatable via PATCH)
- `@ManyToOne(fetch = FetchType.LAZY)` on `assignee` (nullable)
- `@PrePersist` sets `createdAt` and `updatedAt`; `@PreUpdate` refreshes `updatedAt`
- `deletedAt` is set explicitly by the service — no lifecycle hook

### `TicketRepository.java` — Repository

```java
Optional<Ticket> findByIdAndDeletedAtIsNull(Long id);
List<Ticket>     findAllByProjectIdAndDeletedAtIsNullOrderByIdAsc(Long projectId);
boolean          existsByProjectIdAndDeletedAtIsNull(Long projectId);  // used to guard list on deleted project
```

Soft-delete is a save (entity update), not a derived delete query.

### `TicketService.java` — Service

```
create(request):
  1. Resolve project: projectRepository.findByIdAndDeletedAtIsNull(projectId)
     → NotFoundException if absent or soft-deleted
  2. Resolve assignee if assigneeId present:
     userRepository.findById(assigneeId) → NotFoundException if absent
  3. Map to entity (project + assignee references, not IDs)
  4. save → map to response

getById(id):
  findByIdAndDeletedAtIsNull(id) → NotFoundException if absent or deleted

listByProject(projectId):
  1. projectRepository.findByIdAndDeletedAtIsNull(projectId) → NotFoundException if absent/deleted
  2. ticketRepository.findAllByProjectIdAndDeletedAtIsNullOrderByIdAsc(projectId)
  3. map each to response

update(id, request):
  1. findByIdAndDeletedAtIsNull(id) → NotFoundException
  2. if ticket.status.isTerminal() → throw InvalidStateTransitionException("Ticket is frozen — DONE tickets cannot be modified")
  3. if request.status != null:
       if !ticket.status.isImmediateSuccessor(request.status)
         → throw InvalidStateTransitionException("Status can only advance one step forward: " + ticket.status + " → next valid is " + nextStatus)
       ticket.setStatus(request.status)
  4. Apply remaining non-null fields (title, description, priority, type, dueDate, assigneeId)
     For assigneeId: if present (even null), resolve/clear accordingly
  5. save

softDelete(id):
  findByIdAndDeletedAtIsNull(id) → NotFoundException
  ticket.setDeletedAt(Instant.now())
  save
```

### `TicketMapper.java` — MapStruct

```java
@Mapper
public interface TicketMapper {

  @Mapping(target = "id",          ignore = true)
  @Mapping(target = "createdAt",   ignore = true)
  @Mapping(target = "updatedAt",   ignore = true)
  @Mapping(target = "deletedAt",   ignore = true)
  @Mapping(target = "version",     ignore = true)
  @Mapping(target = "project",     source = "project")
  @Mapping(target = "assignee",    source = "assignee")
  @Mapping(target = "status",      expression = "java(TicketStatus.TODO)")
  Ticket toEntity(CreateTicketRequest request, Project project, User assignee);

  @Mapping(target = "projectId",  source = "project.id")
  @Mapping(target = "assigneeId", source = "assignee.id")
  @Mapping(target = "isOverdue",  expression = "java(ticket.getDueDate() != null && ticket.getDueDate().isBefore(java.time.Instant.now()))")
  TicketResponse toResponse(Ticket ticket);
}
```

`assignee` is passed as a nullable `User` parameter — `null` means no assignee, and MapStruct maps it to `null` in the entity field cleanly.

### `TicketController.java` — Controller

All five methods annotated `@ResponseStatus(HttpStatus.OK)`:

| Method         | Verb             | Path                       | Return type            |
|----------------|------------------|----------------------------|------------------------|
| `create`       | `@PostMapping`   | `/tickets`                 | `TicketResponse`       |
| `listByProject`| `@GetMapping`    | `/tickets`                 | `List<TicketResponse>` |
| `getById`      | `@GetMapping`    | `/tickets/{ticketId}`      | `TicketResponse`       |
| `update`       | `@PatchMapping`  | `/tickets/{ticketId}`      | `void`                 |
| `softDelete`   | `@DeleteMapping` | `/tickets/{ticketId}`      | `void`                 |

`listByProject` takes `@RequestParam Long projectId`.

### `GlobalExceptionHandler.java` — Two new handlers

```java
@ExceptionHandler(InvalidStateTransitionException.class)
public ResponseEntity<ProblemDetail> handleInvalidTransition(InvalidStateTransitionException ex) {
    ProblemDetail problem = ProblemDetailFactory.conflict(ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
}

@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
    ProblemDetail problem = ProblemDetailFactory.conflict(
        "Ticket was modified concurrently. Fetch the latest version and retry.");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
}
```

### `TicketServiceTest.java` — Unit Tests (13 tests)

Pure JVM — mock `TicketRepository`, `ProjectRepository`, `UserRepository`, `TicketMapper` with Mockito.

| # | Test name |
|---|-----------|
| 1 | `createTicket_persistsAndReturnsResponse` |
| 2 | `createTicket_throwsNotFoundWhenProjectAbsent` |
| 3 | `createTicket_throwsNotFoundWhenProjectSoftDeleted` |
| 4 | `createTicket_throwsNotFoundWhenAssigneeAbsent` |
| 5 | `getById_returnsTicketWhenActive` |
| 6 | `getById_throwsNotFoundWhenSoftDeleted` |
| 7 | `listByProject_excludesSoftDeletedTickets` |
| 8 | `update_appliesPriorityChange` |
| 9 | `update_throwsConflictWhenTicketIsDone` |
| 10 | `update_throwsConflictOnBackwardTransition` |
| 11 | `update_throwsConflictOnSkipStepTransition` |
| 12 | `update_advancesStatusWhenValidTransition` |
| 13 | `softDelete_setsDeletedAt` |

Note: `ObjectOptimisticLockingFailureException` is tested at the Postman layer (requires actual DB concurrency) — a pure-JVM unit test cannot simulate JPA version collisions without a real `EntityManager`.

### Postman — Tickets Folder (14 requests)

1. Create ticket — valid → 200 with body (`status = TODO`, `isOverdue = false`)
2. Create ticket — missing title → 422
3. Create ticket — unknown projectId → 404
4. Create ticket — invalid priority → 400
5. List tickets by projectId → 200 array
6. Get ticket by ID — active → 200
7. Get ticket by ID — after soft-delete → 404
8. Update ticket — valid priority change → 200 empty
9. Transition: TODO → IN_PROGRESS → 200 empty
10. Transition: IN_PROGRESS → IN_REVIEW → 200 empty
11. Transition: IN_REVIEW → DONE → 200 empty
12. Backward transition → 409
13. Update DONE ticket → 409 (frozen)
14. Delete ticket → 200 empty; re-delete → 404
15. *(bonus)* No auth → 401

All requests inherit the collection-level bearer token variable.

## Complexity Tracking

> **No constitution violations — no entries needed.**
