---

description: "Task list for Tickets — Create, Read, Update, Transition, Soft-Delete"
---

# Tasks: Tickets — Create, Read, Update, Transition, Soft-Delete

**Input**: Design documents from `specs/005-tickets-crud/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, contracts/tickets.openapi.yaml ✓, quickstart.md ✓

**Organization**: Tasks are grouped by user story (US1–US5) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story?] Description — file path`

- **[P]**: Can run in parallel with other [P] tasks in the same phase (different files, no inter-dependencies)
- **[Story]**: Which user story this task belongs to (US1–US5)

## Path Convention

All source paths are relative to repository root.

---

## Phase 1: Setup (Database Migration)

**Purpose**: Apply the only structural prerequisite — the `tickets` table — before any code is written. Flyway picks this up automatically on next app start.

- [X] T001 Create Flyway migration `src/main/resources/db/migration/V6__tickets.sql` with the `tickets` table and `ix_tickets_project_active` partial index per data-model.md

**Checkpoint**: Start the app; confirm Flyway logs `Successfully applied migration V6__tickets.sql`.

---

## Phase 2: Foundational (Shared Infrastructure)

**Purpose**: Enums, shared exception, DTOs, entity, repository, and mapper that ALL five user stories depend on. No story implementation can start until this phase is complete.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Layer A — Independent stubs (run all in parallel)

- [X] T002 [P] Create `TicketStatus` enum (TODO, IN_PROGRESS, IN_REVIEW, DONE) with `isTerminal()` and `isImmediateSuccessor()` helpers in `src/main/java/com/att/tdp/issueflow/ticket/TicketStatus.java`
- [X] T003 [P] Create `TicketPriority` enum (LOW, MEDIUM, HIGH, CRITICAL) in `src/main/java/com/att/tdp/issueflow/ticket/TicketPriority.java`
- [X] T004 [P] Create `TicketType` enum (BUG, FEATURE, TECHNICAL) in `src/main/java/com/att/tdp/issueflow/ticket/TicketType.java`
- [X] T005 [P] Create `InvalidStateTransitionException extends RuntimeException` (message-only constructor) in `src/main/java/com/att/tdp/issueflow/common/error/InvalidStateTransitionException.java`

### Layer B — DTOs (depend on enums T002–T004, run in parallel after Layer A)

- [X] T006 [P] Create `CreateTicketRequest` DTO with `@NotBlank @Size(max=255)` on `title`, `@NotNull` on `priority` and `type`, optional `assigneeId`, `dueDate` in `src/main/java/com/att/tdp/issueflow/ticket/dto/CreateTicketRequest.java`
- [X] T007 [P] Create `UpdateTicketRequest` DTO with all optional fields (`title`, `description`, `priority`, `type`, `assigneeId`, `dueDate`, `status`) and at-least-one-non-null class-level validation in `src/main/java/com/att/tdp/issueflow/ticket/dto/UpdateTicketRequest.java`
- [X] T008 [P] Create `TicketResponse` DTO with all response fields including `isOverdue boolean` in `src/main/java/com/att/tdp/issueflow/ticket/dto/TicketResponse.java`

### Layer C — Entity (depends on T002–T004)

- [X] T009 Create `Ticket` JPA entity with `@Version Long version`, `@Enumerated(STRING)` on status/priority/type, `@ManyToOne(LAZY)` on project and assignee, `@PrePersist`/`@PreUpdate` lifecycle hooks, `deletedAt Instant` in `src/main/java/com/att/tdp/issueflow/ticket/Ticket.java`

### Layer D — Repository (depends on T009)

- [X] T010 Create `TicketRepository extends JpaRepository<Ticket, Long>` with `findByIdAndDeletedAtIsNull(Long)`, `findAllByProjectIdAndDeletedAtIsNullOrderByIdAsc(Long)` in `src/main/java/com/att/tdp/issueflow/ticket/TicketRepository.java`

### Layer E — Mapper (depends on T009, T006, T008)

- [X] T011 Create `TicketMapper` MapStruct interface with `toEntity(CreateTicketRequest, Project, User)` (status fixed to `TicketStatus.TODO`) and `toResponse(Ticket)` (maps `project.id`, `assignee.id`, computes `isOverdue` via expression) and Javadoc on all public methods in `src/main/java/com/att/tdp/issueflow/ticket/TicketMapper.java`

**Checkpoint**: `./mvnw compile` passes with zero errors — all stubs visible to the compiler.

---

## Phase 3: User Story 1 — Create a Ticket (Priority: P1) 🎯 MVP

**Goal**: `POST /tickets` accepts a valid request and returns a `TicketResponse` with `status = TODO`. Project and assignee lookups are validated.

**Independent Test**: `POST /tickets` with valid body → 200 OK, `status = "TODO"`. Missing title → 422. Unknown `projectId` → 404. Unknown `assigneeId` → 404.

### Tests for User Story 1

- [X] T012 [P] [US1] Write 4 unit tests in `src/test/java/com/att/tdp/issueflow/ticket/TicketServiceTest.java`: `createTicket_persistsAndReturnsResponse`, `createTicket_throwsNotFoundWhenProjectAbsent`, `createTicket_throwsNotFoundWhenProjectSoftDeleted`, `createTicket_throwsNotFoundWhenAssigneeAbsent` — mock `TicketRepository`, `ProjectRepository`, `UserRepository`, `TicketMapper`; confirm tests **fail** before implementation

### Implementation for User Story 1

- [X] T013 [US1] Implement `TicketService.create(CreateTicketRequest)` in `src/main/java/com/att/tdp/issueflow/ticket/TicketService.java`: resolve project (NotFoundException if absent/soft-deleted), resolve assignee if present (NotFoundException if absent), map via `TicketMapper.toEntity`, save, return `toResponse`; add Javadoc
- [X] T014 [US1] Implement `POST /tickets` (`createTicket`) in `src/main/java/com/att/tdp/issueflow/ticket/TicketController.java` with `@ResponseStatus(OK)`, `@RequestBody @Valid CreateTicketRequest`, return `TicketResponse`; add Javadoc

**Checkpoint**: `POST /tickets` returns 200 with `status = "TODO"`. Missing title returns 422. Unknown project returns 404. T012 unit tests pass.

---

## Phase 4: User Story 2 — Fetch and List Tickets (Priority: P1)

**Goal**: `GET /tickets?projectId=` returns a plain JSON array of active tickets. `GET /tickets/{ticketId}` returns a single active ticket. Soft-deleted tickets are invisible.

**Independent Test**: After creating a ticket (Phase 3), `GET /tickets?projectId=1` returns it. After soft-deleting, it is absent. `GET /tickets/999` returns 404.

### Tests for User Story 2

- [X] T015 [P] [US2] Write 3 unit tests in `src/test/java/com/att/tdp/issueflow/ticket/TicketServiceTest.java`: `getById_returnsTicketWhenActive`, `getById_throwsNotFoundWhenSoftDeleted`, `listByProject_excludesSoftDeletedTickets` — confirm tests **fail** before implementation

### Implementation for User Story 2

- [X] T016 [P] [US2] Implement `TicketService.getById(Long)` in `src/main/java/com/att/tdp/issueflow/ticket/TicketService.java`: `findByIdAndDeletedAtIsNull` → NotFoundException if absent; return `toResponse`; add Javadoc
- [X] T017 [P] [US2] Implement `TicketService.listByProject(Long)` in `src/main/java/com/att/tdp/issueflow/ticket/TicketService.java`: validate project exists and is active → NotFoundException, then `findAllByProjectIdAndDeletedAtIsNullOrderByIdAsc`, map each to response; add Javadoc
- [X] T018 [US2] Implement `GET /tickets` (`listByProject`) and `GET /tickets/{ticketId}` (`getById`) in `src/main/java/com/att/tdp/issueflow/ticket/TicketController.java` with `@ResponseStatus(OK)`; `listByProject` takes `@RequestParam Long projectId` and returns `List<TicketResponse>`; add Javadoc

**Checkpoint**: `GET /tickets?projectId=1` returns a plain JSON array. `GET /tickets/{id}` for a soft-deleted ticket returns 404. T015 unit tests pass.

---

## Phase 5: User Story 3 — Update Ticket Fields (Priority: P2)

**Goal**: `PATCH /tickets/{ticketId}` with non-status fields updates the ticket if not in `DONE` status. Attempting to update a `DONE` ticket returns 409.

**Independent Test**: `PATCH /tickets/{id}` with `{ "priority": "CRITICAL" }` on a TODO ticket → 200 OK empty. Same call on DONE ticket → 409. Empty body `{}` → 400.

### Tests for User Story 3

- [X] T019 [P] [US3] Write 2 unit tests in `src/test/java/com/att/tdp/issueflow/ticket/TicketServiceTest.java`: `update_appliesPriorityChange`, `update_throwsConflictWhenTicketIsDone` — confirm tests **fail** before implementation

### Implementation for User Story 3

- [X] T020 [US3] Implement `TicketService.update(Long, UpdateTicketRequest)` field-update path in `src/main/java/com/att/tdp/issueflow/ticket/TicketService.java`: fetch active ticket → NotFoundException; if `ticket.status.isTerminal()` throw `InvalidStateTransitionException("Ticket is frozen — DONE tickets cannot be modified")`; apply non-null fields (title, description, priority, type, dueDate, assigneeId — resolve/clear assignee accordingly); save; add Javadoc
- [X] T021 [US3] Implement `PATCH /tickets/{ticketId}` (`update`) in `src/main/java/com/att/tdp/issueflow/ticket/TicketController.java` with `@ResponseStatus(OK)`, `@RequestBody @Valid UpdateTicketRequest`, return `void`; add Javadoc

**Checkpoint**: `PATCH /tickets/{id}` with `{ "priority": "LOW" }` returns 200 empty. Same on DONE ticket returns 409. T019 unit tests pass.

---

## Phase 6: User Story 4 — Advance Ticket Status (Priority: P2)

**Goal**: `PATCH /tickets/{ticketId}` with `{ "status": "IN_PROGRESS" }` advances a `TODO` ticket. Backward and skip-step transitions return 409. Concurrent updates return 409 (optimistic lock).

**Independent Test**: `PATCH` TODO→IN_PROGRESS → 200. IN_PROGRESS→TODO → 409. TODO→DONE (skip) → 409. Concurrent `PATCH` → one 200, one 409.

### Tests for User Story 4

- [X] T022 [P] [US4] Write 3 unit tests in `src/test/java/com/att/tdp/issueflow/ticket/TicketServiceTest.java`: `update_throwsConflictOnBackwardTransition`, `update_throwsConflictOnSkipStepTransition`, `update_advancesStatusWhenValidTransition` — confirm tests **fail** before implementation

### Implementation for User Story 4

- [X] T023 [US4] Extend `TicketService.update()` with status-transition logic in `src/main/java/com/att/tdp/issueflow/ticket/TicketService.java`: when `request.status != null`, call private `validateTransition(current, requested)` that throws `InvalidStateTransitionException` if `!current.isImmediateSuccessor(requested)` with a descriptive message; then set `ticket.setStatus(request.status)`
- [X] T024 [US4] Add `handleInvalidTransition(InvalidStateTransitionException)` and `handleOptimisticLock(ObjectOptimisticLockingFailureException)` `@ExceptionHandler` methods to `src/main/java/com/att/tdp/issueflow/common/error/GlobalExceptionHandler.java` — both return `ResponseEntity` with status `409 Conflict` and a `ProblemDetail` via `ProblemDetailFactory.conflict()`

**Checkpoint**: Forward transition TODO→IN_PROGRESS returns 200. Backward TODO→IN_PROGRESS (when already IN_PROGRESS→TODO) returns 409. Skip-step TODO→DONE returns 409. T022 unit tests pass.

---

## Phase 7: User Story 5 — Soft-Delete a Ticket (Priority: P3)

**Goal**: `DELETE /tickets/{ticketId}` sets `deletedAt` on the ticket. Subsequent GET and LIST calls exclude it. Re-deleting returns 404.

**Independent Test**: Create ticket → `DELETE /tickets/{id}` → 200 empty → `GET /tickets/{id}` → 404 → `GET /tickets?projectId=` → ticket absent. Re-delete → 404.

### Tests for User Story 5

- [X] T025 [P] [US5] Write 1 unit test in `src/test/java/com/att/tdp/issueflow/ticket/TicketServiceTest.java`: `softDelete_setsDeletedAt` — confirm test **fails** before implementation

### Implementation for User Story 5

- [X] T026 [US5] Implement `TicketService.softDelete(Long)` in `src/main/java/com/att/tdp/issueflow/ticket/TicketService.java`: `findByIdAndDeletedAtIsNull` → NotFoundException; set `ticket.setDeletedAt(Instant.now())`; save (soft-delete is a regular save, not a derived delete); add Javadoc
- [X] T027 [US5] Implement `DELETE /tickets/{ticketId}` (`softDelete`) in `src/main/java/com/att/tdp/issueflow/ticket/TicketController.java` with `@ResponseStatus(OK)`, return `void`; add Javadoc

**Checkpoint**: `DELETE /tickets/{id}` returns 200 empty. Second DELETE returns 404. Ticket absent from GET list. T025 unit test passes.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, Postman collection, and final smoke-test validation.

- [X] T028 [P] Add Javadoc to all public methods in `src/main/java/com/att/tdp/issueflow/ticket/TicketMapper.java` if any are missing (constitution III)
- [X] T029 [P] Update Postman collection `postman/issueflow.postman_collection.json` with the new "Tickets" folder containing all 14 requests per plan.md (create valid, missing title, unknown projectId, invalid priority, list, get by ID, get after delete, update priority, TODO→IN_PROGRESS, IN_PROGRESS→IN_REVIEW, IN_REVIEW→DONE, backward transition, DONE freeze, soft-delete + re-delete, no-auth)
- [X] T030 Add prompts.md entry documenting this planning session per constitution III
- [X] T031 Run quickstart.md smoke tests end-to-end to validate all five user stories

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Migration)**: No dependencies — can start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all user stories
  - Layer A (T002–T005): All parallel, no inter-dependencies
  - Layer B (T006–T008): Parallel, depend on Layer A enums (T002–T004)
  - Layer C (T009): Depends on Layer A enums (T002–T004)
  - Layer D (T010): Depends on entity (T009)
  - Layer E (T011): Depends on entity (T009) and DTOs (T006, T008)
- **Phase 3 (US1)**: Depends on Phase 2 completion — no story dependencies
- **Phase 4 (US2)**: Depends on Phase 2 completion — logically requires US1 data to exist for integration testing, but service methods are independent
- **Phase 5 (US3)**: Depends on Phase 2 + US1 service (`update` calls `findByIdAndDeletedAtIsNull`)
- **Phase 6 (US4)**: Depends on Phase 5 — extends the same `update()` method
- **Phase 7 (US5)**: Depends on Phase 2 — service method is independent
- **Phase 8 (Polish)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (P1)**: Foundational phase complete — no story dependencies
- **US2 (P1)**: Foundational phase complete — no story dependencies (can develop in parallel with US1)
- **US3 (P2)**: Foundational + US1 service layer needed (re-uses entity fetch pattern)
- **US4 (P2)**: Foundational + US3 complete (extends US3's `update()` method)
- **US5 (P3)**: Foundational phase complete — no story dependencies

### Within Each User Story

- Unit tests must be written first and confirmed to **fail** before implementation
- Service layer before controller layer
- Story complete before moving to next priority

### Parallel Opportunities

- All Layer A foundational tasks (T002–T005) in parallel
- All Layer B DTO tasks (T006–T008) in parallel after T002–T004
- US1 and US2 can proceed in parallel after Phase 2 is complete
- US5 can proceed in parallel with US3–US4 after Phase 2 is complete
- T028–T029 in the Polish phase can run in parallel

---

## Parallel Example: Phase 2 Foundational

```
# Start immediately (Layer A):
T002 TicketStatus enum
T003 TicketPriority enum
T004 TicketType enum
T005 InvalidStateTransitionException

# After T002–T004 complete (Layer B — all parallel):
T006 CreateTicketRequest DTO
T007 UpdateTicketRequest DTO
T008 TicketResponse DTO
T009 Ticket entity

# After T009 (Layer D):
T010 TicketRepository

# After T009 + T006 + T008 (Layer E):
T011 TicketMapper
```

## Parallel Example: User Story 1

```
# Write tests first (fail before impl):
T012 TicketServiceTest create tests

# After T012, implement in order:
T013 TicketService.create()
T014 TicketController POST /tickets
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Migration)
2. Complete Phase 2 (Foundational — CRITICAL)
3. Complete Phase 3 (US1 — Create Ticket)
4. **STOP and VALIDATE**: `POST /tickets` → 200 with `status = TODO`; unit tests pass
5. Demo / ship MVP

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready
2. Phase 3 (US1) → Create works → validate independently
3. Phase 4 (US2) → Read/List works → validate independently
4. Phase 5 (US3) → Field update works → validate independently
5. Phase 6 (US4) → Status transitions + concurrency → validate independently
6. Phase 7 (US5) → Soft-delete works → validate independently
7. Phase 8 (Polish) → Postman, docs, smoke test

### Parallel Team Strategy (3 developers post-Phase-2)

- Developer A: US1 (Phase 3)
- Developer B: US2 (Phase 4)
- Developer C: US5 (Phase 7)
- Developer A/B merge → Developer A: US3 (Phase 5) → US4 (Phase 6)

---

## Notes

- [P] = different files, no incomplete-task dependencies — safe to run in parallel
- [Story] label maps each task to a user story for traceability
- Unit tests use pure JVM (Mockito mocks only) — no Spring context required
- Optimistic lock concurrency (`ObjectOptimisticLockingFailureException`) is tested via Postman, not unit tests (requires real JPA EntityManager)
- All five controller methods use `@ResponseStatus(HttpStatus.OK)` per README canonical table — no 201 or 204
- `GET /tickets?projectId=` returns a plain `List<TicketResponse>` (not `PagedResponse`) per research Decision 7
- Status transitions go through `PATCH /tickets/{ticketId}` with `status` field — no separate transition endpoint per research Decision 2
