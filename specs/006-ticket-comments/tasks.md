# Tasks: Ticket Comments

**Input**: Design documents from `/specs/006-ticket-comments/`
**Prerequisites**: plan.md ✅, spec.md ✅, data-model.md ✅, research.md ✅, contracts/ ✅, quickstart.md ✅

**Tests**: Included — specified by constitution gate II in plan.md and the 10 named unit-test scenarios.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Exact file paths included in all task descriptions

## Path Conventions

- Source root: `src/main/java/com/att/tdp/issueflow/`
- Test root: `src/test/java/com/att/tdp/issueflow/`
- Resources: `src/main/resources/`
- Postman: `postman/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Enable JPA auditing globally and create the database migration required by the Comment entity.

- [X] T001 Add `@EnableJpaAuditing` annotation to `src/main/java/com/att/tdp/issueflow/IssueFlowApplication.java`
- [X] T002 Create Flyway migration with `comments` table (`id`, `content TEXT NOT NULL`, `ticket_id BIGINT FK`, `author_id BIGINT FK`, `created_at TIMESTAMPTZ`, `updated_at TIMESTAMPTZ`, `version BIGINT DEFAULT 0`) and `idx_comments_ticket_id` index in `src/main/resources/db/migration/V7__comments.sql`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: All shared types — entity, DTOs, mapper, repository, and exception — that every user story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Create `StaleVersionException extends RuntimeException` with hard-coded message `"Resource has been modified by another request; re-fetch and retry."` in `src/main/java/com/att/tdp/issueflow/common/error/StaleVersionException.java`
- [X] T004 [P] Create `Comment` entity annotated `@Entity @Table(name="comments") @EntityListeners(AuditingEntityListener.class) @Getter @Setter @NoArgsConstructor` with fields: `@Id @GeneratedValue id`, `@Column(columnDefinition="TEXT") content`, `@ManyToOne(fetch=LAZY) @JoinColumn(name="ticket_id", updatable=false) ticket`, `@ManyToOne(fetch=LAZY) @JoinColumn(name="author_id", updatable=false) author`, `@CreatedDate createdAt`, `@LastModifiedDate updatedAt`, `@Version version` in `src/main/java/com/att/tdp/issueflow/comment/Comment.java`
- [X] T005 [P] Create `CreateCommentRequest` record with `@NotBlank @Size(max = 2000) String content` in `src/main/java/com/att/tdp/issueflow/comment/dto/CreateCommentRequest.java`
- [X] T006 [P] Create `UpdateCommentRequest` record with `@NotBlank @Size(max = 2000) String content` and `@NotNull Long version` in `src/main/java/com/att/tdp/issueflow/comment/dto/UpdateCommentRequest.java`
- [X] T007 [P] Create `CommentResponse` record with fields `Long id`, `String content`, `Long ticketId`, `Long authorId`, `Instant createdAt`, `Instant updatedAt`, `Long version` in `src/main/java/com/att/tdp/issueflow/comment/dto/CommentResponse.java`
- [X] T008 Add `@ExceptionHandler(StaleVersionException.class)` handler returning `409 Conflict` via `ProblemDetailFactory` to existing `src/main/java/com/att/tdp/issueflow/common/error/GlobalExceptionHandler.java`
- [X] T009 Create `CommentMapper` (`@Mapper(componentModel = "spring")`) with `toResponse(Comment)` mapping `ticket.id → ticketId` and `author.id → authorId`, and `toEntity(CreateCommentRequest)` ignoring `id`, `ticket`, `author`, `createdAt`, `updatedAt`, `version` in `src/main/java/com/att/tdp/issueflow/comment/CommentMapper.java`
- [X] T010 Create `CommentRepository` extending `JpaRepository<Comment, Long>` with `Page<Comment> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId, Pageable pageable)` in `src/main/java/com/att/tdp/issueflow/comment/CommentRepository.java`

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 — Post a Comment (Priority: P1) 🎯 MVP

**Goal**: Any authenticated user can post a plain-text comment on a ticket; the author is set automatically from the JWT; the new comment is immediately persisted and returned.

**Independent Test**: `POST /tickets/{ticketId}/comments` with `{"content":"text"}` returns `200 OK` with a comment object containing generated `id`, `authorId` from JWT, `ticketId`, `content`, timestamps, and `version: 0`. POST with blank content returns `400`. POST on non-existent ticket returns `404`.

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T011 [US1] Create `CommentServiceTest` with Mockito mocks for `CommentRepository`, `TicketRepository`, `UserRepository`, `CommentMapper`; write failing tests `createsCommentSuccessfully`, `throwsNotFoundWhenTicketAbsentOnCreate`, `throwsNotFoundWhenAuthorNotFound` in `src/test/java/com/att/tdp/issueflow/comment/CommentServiceTest.java`

### Implementation for User Story 1

- [X] T012 [US1] Create `CommentService` (`@Service`) with `createComment(Long ticketId, CreateCommentRequest request, String authorUsername)`: call `ticketRepository.findByIdAndDeletedAtIsNull(ticketId).orElseThrow(NotFoundException)`, load `User` via `userRepository.findByUsername(authorUsername).orElseThrow(NotFoundException)`, map request → entity via mapper, set `ticket` and `author`, save, return mapped `CommentResponse` in `src/main/java/com/att/tdp/issueflow/comment/CommentService.java`
- [X] T013 [US1] Create `CommentController` (`@RestController @RequestMapping("/tickets/{ticketId}/comments")`) with `@PostMapping` handler accepting `@Valid @RequestBody CreateCommentRequest` and `@AuthenticationPrincipal UserDetails principal`, delegating to `commentService.createComment(ticketId, request, principal.getUsername())` in `src/main/java/com/att/tdp/issueflow/comment/CommentController.java`

**Checkpoint**: `POST /tickets/{ticketId}/comments` fully functional and independently testable.

---

## Phase 4: User Story 2 — List Comments on a Ticket (Priority: P1)

**Goal**: Any authenticated user can retrieve all comments on a ticket in chronological order via a paginated response.

**Independent Test**: `GET /tickets/{ticketId}/comments` returns `200 OK` with a `PagedResponse<CommentResponse>` ordered by `createdAt` ASC. A ticket with no comments returns `200 OK` with an empty `data` array. Non-existent ticket returns `404`.

### Tests for User Story 2

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T014 [US2] Add failing tests `listsCommentsOrderedByCreatedAt`, `throwsNotFoundWhenTicketAbsentOnList` to `src/test/java/com/att/tdp/issueflow/comment/CommentServiceTest.java`

### Implementation for User Story 2

- [X] T015 [US2] Add `listComments(Long ticketId, Pageable pageable)` to `CommentService`: validate ticket via `ticketRepository.findByIdAndDeletedAtIsNull`, call `commentRepository.findAllByTicketIdOrderByCreatedAtAsc(ticketId, pageable)`, map page to `PagedResponse<CommentResponse>` using the project's canonical `PagedResponse` builder in `src/main/java/com/att/tdp/issueflow/comment/CommentService.java`
- [X] T016 [US2] Add `@GetMapping` handler to `CommentController` accepting `Pageable` (with `@PageableDefault` if applicable), calling `commentService.listComments(ticketId, pageable)` in `src/main/java/com/att/tdp/issueflow/comment/CommentController.java`

**Checkpoint**: `GET /tickets/{ticketId}/comments` fully functional; Stories 1 AND 2 independently testable.

---

## Phase 5: User Story 3 — Edit a Comment (Priority: P2)

**Goal**: Any authenticated user can update a comment's content; the caller must supply the current version and gets `409 Conflict` if someone else has edited it concurrently.

**Independent Test**: `PATCH /tickets/{ticketId}/comments/{commentId}` with `{"content":"new text","version":0}` returns `200 OK` with updated content and `version: 1`. Same call with stale `version` returns `409 Conflict`. Missing `version` field returns `400`. Non-existent comment returns `404`.

### Tests for User Story 3

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T017 [US3] Add failing tests `updatesCommentSuccessfully`, `throwsStaleVersionExceptionOnVersionMismatch`, `throwsNotFoundWhenCommentAbsentOnUpdate` to `src/test/java/com/att/tdp/issueflow/comment/CommentServiceTest.java`

### Implementation for User Story 3

- [X] T018 [US3] Add `updateComment(Long ticketId, Long commentId, UpdateCommentRequest request)` to `CommentService`: load comment by ID (`orElseThrow NotFoundException`), check `request.version().equals(comment.getVersion())` (throw `StaleVersionException` if mismatched), call `comment.setContent(request.content())`, call `commentRepository.save(comment)` (JPA `@Version` auto-increments), return mapped `CommentResponse` in `src/main/java/com/att/tdp/issueflow/comment/CommentService.java`
- [X] T019 [US3] Add `@PatchMapping("/{commentId}")` handler to `CommentController` accepting `@Valid @RequestBody UpdateCommentRequest`, delegating to `commentService.updateComment(ticketId, commentId, request)` in `src/main/java/com/att/tdp/issueflow/comment/CommentController.java`

**Checkpoint**: `PATCH /tickets/{ticketId}/comments/{commentId}` fully functional; `409 Conflict` returned on stale version.

---

## Phase 6: User Story 4 — Delete a Comment (Priority: P3)

**Goal**: Any authenticated user can permanently remove a comment; it disappears from all subsequent list responses (hard delete, no soft-delete).

**Independent Test**: `DELETE /tickets/{ticketId}/comments/{commentId}` returns `200 OK`. Subsequent `GET` on the same ticket does not include the deleted comment. Delete on non-existent comment returns `404`.

### Tests for User Story 4

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T020 [US4] Add failing tests `deletesCommentSuccessfully`, `throwsNotFoundWhenCommentAbsentOnDelete` to `src/test/java/com/att/tdp/issueflow/comment/CommentServiceTest.java`

### Implementation for User Story 4

- [X] T021 [US4] Add `deleteComment(Long ticketId, Long commentId)` to `CommentService`: load comment by ID via `commentRepository.findById(commentId).orElseThrow(NotFoundException)`, call `commentRepository.delete(comment)` in `src/main/java/com/att/tdp/issueflow/comment/CommentService.java`
- [X] T022 [US4] Add `@DeleteMapping("/{commentId}")` handler to `CommentController` with `void` return type (project convention: `200 OK` with empty body), delegating to `commentService.deleteComment(ticketId, commentId)` in `src/main/java/com/att/tdp/issueflow/comment/CommentController.java`

**Checkpoint**: All four CRUD stories independently functional and tested.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Integration test collection and API documentation updates.

- [X] T023 [P] Create Postman collection with: auth setup re-using existing `authToken` env variable; `POST` (happy path, blank content → 400, missing ticket → 404); `GET` (with results, empty list, deleted ticket → 404); `PATCH` (happy path, stale version → 409, missing comment → 404); `DELETE` (happy path, missing comment → 404) in `postman/comments.postman_collection.json`
- [X] T024 [P] Add four rows to the API table in `README.md`: `GET /tickets/:ticketId/comments` (200 OK, paginated list), `POST /tickets/:ticketId/comments` (200 OK, comment object), `PATCH /tickets/:ticketId/comments/:commentId` (200 OK, updated comment), `DELETE /tickets/:ticketId/comments/:commentId` (200 OK)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS all user stories
- **User Stories (Phases 3–6)**: All depend on Phase 2 completion
  - US1 and US2 (both P1) can be worked in parallel after Phase 2 if on separate branches
  - US3 (P2) and US4 (P3) also only depend on Phase 2, not on US1/US2 code
- **Polish (Phase 7)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (P1)**: Creates `CommentService.java` and `CommentController.java` files — no prior story dependency
- **US2 (P1)**: Adds methods to files created in US1 — must merge US1 first if sequential, or work in parallel on separate branches
- **US3 (P2)**: Adds `updateComment` to existing files — no logic dependency on US1/US2
- **US4 (P3)**: Adds `deleteComment` to existing files — no logic dependency on US1–US3

### Within Each User Story

1. Write tests → confirm they FAIL
2. Implement service method
3. Implement controller method (depends on service)
4. Confirm tests PASS

### Parallel Opportunities

- T003–T007 in Phase 2 touch different files — run all in parallel
- T008 (GlobalExceptionHandler edit) and T009 (new CommentMapper file) can run in parallel with T010 (new CommentRepository file)
- T023 and T024 in Phase 7 touch different files — run in parallel

---

## Parallel Example: Phase 2 Foundational

```bash
# These five tasks touch separate new files — run together:
T003: StaleVersionException.java       (new file)
T004: Comment.java                     (new file)
T005: CreateCommentRequest.java        (new file)
T006: UpdateCommentRequest.java        (new file)
T007: CommentResponse.java             (new file)

# Then in sequence (each depends on T004/T005/T006/T007):
T008: GlobalExceptionHandler.java      (edit existing — add handler method)
T009: CommentMapper.java               (new file — depends on Comment + DTOs)
T010: CommentRepository.java           (new file — depends on Comment)
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks everything)
3. Complete Phase 3: US1 — Post a Comment
4. Complete Phase 4: US2 — List Comments
5. **STOP and VALIDATE**: POST then GET shows the comment in the list
6. Demo the post-and-list workflow

### Incremental Delivery

1. Setup + Foundational → entity, DTOs, mapper, repository ready
2. US1 → Comments can be posted (MVP!)
3. US2 → Posted comments can be listed
4. US3 → Comments can be edited with conflict protection
5. US4 → Comments can be deleted
6. Polish → Postman collection + README

### Parallel Team Strategy

With multiple developers after Phase 2 completes:
- Developer A: US1 + US2 (both P1, sequential or on separate branches)
- Developer B: US3 (P2)
- Developer C: US4 (P3)
- Each story merges independently after its checkpoint passes

---

## Notes

- `[P]` tasks touch different files and have no incomplete task dependencies
- `[USn]` label maps each task to a specific user story for traceability
- All endpoints return `200 OK` on success — including POST and DELETE (project-wide README convention; see research Decision 4)
- Comment text field is `content` in all code (not `body` as in the original spec prose — see research Decision 8)
- Endpoint path is `/tickets/{ticketId}/comments` — not nested under `/projects/` (see research Decision 3)
- `@EnableJpaAuditing` in Phase 1 is additive; existing entities using `@PrePersist`/`@PreUpdate` are unaffected
- US2–US4 add methods to `CommentService.java` and `CommentController.java` files first created in US1
- Verify the next available Flyway migration version under `src/main/resources/db/migration/` before finalising `V7__comments.sql`
