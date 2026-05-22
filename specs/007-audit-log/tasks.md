# Tasks: Audit Log

**Input**: Design documents from `/specs/007-audit-log/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/audit-logs.openapi.yaml](contracts/audit-logs.openapi.yaml), [quickstart.md](quickstart.md)

**Tests**: Included — Constitution Principle II makes both pure-JVM unit tests AND Postman tests **mandatory** for this feature (concurrency-adjacent behaviour: thread-local hint, listener callbacks; branching: filter combinations; security: ADMIN gate).

**Organization**: Tasks are grouped by user story so each story can be implemented and validated independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Exact file paths included in every task

## Path Conventions

- Source: [src/main/java/com/att/tdp/issueflow/](src/main/java/com/att/tdp/issueflow/)
- Tests: [src/test/java/com/att/tdp/issueflow/](src/test/java/com/att/tdp/issueflow/)
- Resources: [src/main/resources/](src/main/resources/)
- Postman: [postman/](postman/)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the new feature package; everything else (Maven, Flyway, JPA, Spring Security, MapStruct, Lombok, Spotless) is already wired.

- [ ] T001 Create new package directory [src/main/java/com/att/tdp/issueflow/auditlog/](src/main/java/com/att/tdp/issueflow/auditlog/) with [src/main/java/com/att/tdp/issueflow/auditlog/package-info.java](src/main/java/com/att/tdp/issueflow/auditlog/package-info.java) (one-line javadoc: "Audit-log feature slice: immutable record of every CREATE/UPDATE/DELETE on audited entities. Read-only HTTP surface gated on ADMIN authority.")

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, entity, enums, repository, and cross-cutting helpers that every user story depends on. No story can begin until this phase is complete.

**⚠️ CRITICAL**: This phase only delivers the *plumbing* — no audit entry is written and no endpoint is exposed yet. Those are in Phase 3 and Phase 4.

- [ ] T002 Create Flyway migration with `audit_logs` table — columns `id BIGSERIAL PK`, `action VARCHAR(32) NOT NULL`, `entity_type VARCHAR(32) NOT NULL`, `entity_id BIGINT NOT NULL`, `performed_by BIGINT NULL`, `actor VARCHAR(16) NOT NULL`, `timestamp TIMESTAMPTZ NOT NULL`, `details JSONB NULL`; indexes `idx_audit_logs_timestamp_desc (timestamp DESC)`, `idx_audit_logs_entity_type (entity_type)`, `idx_audit_logs_entity (entity_type, entity_id)`, `idx_audit_logs_performed_by (performed_by)`, `idx_audit_logs_action (action)`; no `REFERENCES` clauses (FR-011 requires entries to outlive the rows they describe) in [src/main/resources/db/migration/V8__audit_logs.sql](src/main/resources/db/migration/V8__audit_logs.sql)
- [ ] T003 [P] Create `AuditAction` enum with values `CREATE, UPDATE, DELETE, SOFT_DELETE, RESTORE, STATUS_CHANGE, AUTO_ASSIGN, AUTO_ESCALATE` (the last two are reserved for later phases but encoded now so the schema-allowed strings are stable) in [src/main/java/com/att/tdp/issueflow/auditlog/AuditAction.java](src/main/java/com/att/tdp/issueflow/auditlog/AuditAction.java)
- [ ] T004 [P] Create `AuditEntityType` enum with values `USER, PROJECT, TICKET, COMMENT` in [src/main/java/com/att/tdp/issueflow/auditlog/AuditEntityType.java](src/main/java/com/att/tdp/issueflow/auditlog/AuditEntityType.java)
- [ ] T005 [P] Create `ActorKind` enum with values `USER, SYSTEM` in [src/main/java/com/att/tdp/issueflow/auditlog/ActorKind.java](src/main/java/com/att/tdp/issueflow/auditlog/ActorKind.java)
- [ ] T006 Create `AuditLog` entity (`@Entity @Table(name="audit_logs") @Getter @Setter @NoArgsConstructor`) with fields: `@Id @GeneratedValue(strategy=IDENTITY) Long id`; `@Enumerated(STRING) @Column(name="action", nullable=false, updatable=false, length=32) AuditAction action`; `@Enumerated(STRING) @Column(name="entity_type", nullable=false, updatable=false, length=32) AuditEntityType entityType`; `@Column(name="entity_id", nullable=false, updatable=false) Long entityId`; `@Column(name="performed_by", updatable=false) Long performedBy`; `@Enumerated(STRING) @Column(name="actor", nullable=false, updatable=false, length=16) ActorKind actor`; `@Column(name="timestamp", nullable=false, updatable=false) Instant timestamp`; `@JdbcTypeCode(SqlTypes.JSON) @Column(name="details", columnDefinition="jsonb") Map<String,Object> details` in [src/main/java/com/att/tdp/issueflow/auditlog/AuditLog.java](src/main/java/com/att/tdp/issueflow/auditlog/AuditLog.java)
- [ ] T007 Create `AuditLogRepository` extending `JpaRepository<AuditLog, Long>` and `JpaSpecificationExecutor<AuditLog>` in [src/main/java/com/att/tdp/issueflow/auditlog/AuditLogRepository.java](src/main/java/com/att/tdp/issueflow/auditlog/AuditLogRepository.java)
- [ ] T008 [P] Create `AuditContext` utility with `private static final ThreadLocal<AuditAction> HINT = new ThreadLocal<>()`; `public static void hint(AuditAction)`; `public static AuditAction consumeOrDefault(AuditAction fallback)` (reads, then `HINT.remove()`); private constructor in [src/main/java/com/att/tdp/issueflow/auditlog/AuditContext.java](src/main/java/com/att/tdp/issueflow/auditlog/AuditContext.java)
- [ ] T009 [P] Create `SecurityUtil` utility with `public static Optional<String> currentUsername()` reading `SecurityContextHolder.getContext().getAuthentication()` and returning empty for `null`, unauthenticated, or `"anonymousUser"` principal; otherwise returning the username from a `UserDetails` or `String` principal; private constructor; Javadoc on the public method in [src/main/java/com/att/tdp/issueflow/common/security/SecurityUtil.java](src/main/java/com/att/tdp/issueflow/common/security/SecurityUtil.java)
- [ ] T010 [P] Create `SpringContextHolder` bean (`@Component` implementing `ApplicationContextAware`) with `private static ApplicationContext context`; `setApplicationContext(ApplicationContext)` assigning to the static field; `public static <T> T bean(Class<T> type)` delegating to `context.getBean(type)`; Javadoc explains why JPA entity listeners need this in [src/main/java/com/att/tdp/issueflow/common/config/SpringContextHolder.java](src/main/java/com/att/tdp/issueflow/common/config/SpringContextHolder.java)
- [ ] T011 [P] Create `EntityClassifier` utility with `public static AuditEntityType classify(Object entity)` (single `instanceof` switch on `User`, `Project`, `Ticket`, `Comment`) and `public static Long extractId(Object entity)` (calls `getId()` via each branch); throws `IllegalArgumentException` for any other class; private constructor in [src/main/java/com/att/tdp/issueflow/auditlog/EntityClassifier.java](src/main/java/com/att/tdp/issueflow/auditlog/EntityClassifier.java)
- [ ] T012 [P] Create `SecurityUtilTest` with three tests: `returnsEmptyWhenNoAuthenticationPresent` (clears `SecurityContextHolder` and asserts empty), `returnsEmptyForAnonymousPrincipal` (sets an `Authentication` whose principal is the string `"anonymousUser"`), `returnsUsernameFromUserDetailsPrincipal` (sets a `UsernamePasswordAuthenticationToken` with a Spring `User` UserDetails) in [src/test/java/com/att/tdp/issueflow/auditlog/SecurityUtilTest.java](src/test/java/com/att/tdp/issueflow/auditlog/SecurityUtilTest.java)
- [ ] T013 [P] Create `AuditContextTest` with three tests: `consumeReturnsFallbackWhenNoHintSet`, `consumeReturnsHintedActionAndClearsIt` (set, consume → hinted value; consume again → fallback), `hintIsThreadScopedAndDoesNotLeakAcrossThreads` (set hint on one thread, run `CompletableFuture.runAsync(...)` and assert it sees the fallback) in [src/test/java/com/att/tdp/issueflow/auditlog/AuditContextTest.java](src/test/java/com/att/tdp/issueflow/auditlog/AuditContextTest.java)

**Checkpoint**: Schema migrated; entity, enums, repository, three cross-cutting helpers, and their unit tests in place. No audit entry is written yet — that begins in Phase 3.

---

## Phase 3: User Story 1 — Automatic Recording of Entity Changes (Priority: P1) 🎯 MVP-half

**Goal**: Every successful CREATE / UPDATE / DELETE on `User`, `Project`, `Ticket`, `Comment` writes exactly one audit entry capturing actor, action, entity type, entity id, timestamp. Soft delete on Project/Ticket records `SOFT_DELETE` (not generic `UPDATE`); a Ticket status change records `STATUS_CHANGE`. All entries land in the same transaction as the underlying change.

**Independent Test**: Standalone — verify directly against `audit_logs` table (US1 alone has no read API). Pure-JVM: invoke the listener with a stubbed `AuditLogService` and assert the right `record(...)` arguments. End-to-end: per [quickstart.md](quickstart.md), POST a project as an admin and `SELECT * FROM audit_logs WHERE entity_type='PROJECT' AND action='CREATE'` finds the row.

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T014 [US1] Create `AuditLogServiceTest` with Mockito mocks for `AuditLogRepository`, `UserRepository`; failing tests `recordsCreateAuditEntryWithAuthenticatedActor` (stub `SecurityContextHolder` so `SecurityUtil.currentUsername()` returns `"jdoe"`; stub `userRepository.findByUsernameIgnoreCase("jdoe")` to return a `User` with id 7; pass a `Ticket` with id 42; assert the captured `AuditLog` has `action=CREATE`, `entityType=TICKET`, `entityId=42`, `performedBy=7`, `actor=USER`, `timestamp` within the last second), `recordsCreateAuditEntryWithSystemActorWhenNoAuthentication` (clear SecurityContext; assert `performedBy=null`, `actor=SYSTEM`), `recordsCreateAuditEntryWithSystemActorWhenUsernameNotFoundInDatabase` (currentUsername returns `"ghost"` but `userRepository` returns empty → still `actor=SYSTEM`, `performedBy=null`) in [src/test/java/com/att/tdp/issueflow/auditlog/AuditLogServiceTest.java](src/test/java/com/att/tdp/issueflow/auditlog/AuditLogServiceTest.java)
- [ ] T015 [US1] Create `AuditableEntityListenerTest` with Mockito mocks for `AuditLogService` (returned via a `MockedStatic<SpringContextHolder>` so the listener's bean lookup is intercepted); failing tests `onCreateRecordsCreateActionWhenNoHint`, `onUpdateRecordsUpdateActionWhenNoHint`, `onUpdateRecordsHintedActionWhenServiceSetHint` (call `AuditContext.hint(SOFT_DELETE)` before invoking the listener; assert `record(SOFT_DELETE, entity)` was called), `onRemoveRecordsDeleteAction`, `consecutiveOnUpdateCallsRevertToDefaultAfterFirstConsumesHint` (hint set once → first @PostUpdate gets SOFT_DELETE; immediate second @PostUpdate gets UPDATE) in [src/test/java/com/att/tdp/issueflow/auditlog/AuditableEntityListenerTest.java](src/test/java/com/att/tdp/issueflow/auditlog/AuditableEntityListenerTest.java)

### Implementation for User Story 1

- [ ] T016 [US1] Create `AuditLogService` (`@Service`) with `@Transactional public void record(AuditAction action, Object entity)`: derive `entityType` via `EntityClassifier.classify(entity)`, `entityId` via `EntityClassifier.extractId(entity)`, resolve `performedBy` via `SecurityUtil.currentUsername().flatMap(u -> userRepository.findByUsernameIgnoreCase(u)).map(User::getId)`, set `actor = performedBy.isPresent() ? USER : SYSTEM`, populate `timestamp = Instant.now()`, leave `details = null`, call `auditLogRepository.save(...)`; full Javadoc explaining when each field is set in [src/main/java/com/att/tdp/issueflow/auditlog/AuditLogService.java](src/main/java/com/att/tdp/issueflow/auditlog/AuditLogService.java)
- [ ] T017 [US1] Create `AuditableEntityListener` (no Spring annotation — JPA instantiates by reflection) with `@PostPersist public void onCreate(Object entity)` calling `SpringContextHolder.bean(AuditLogService.class).record(AuditContext.consumeOrDefault(AuditAction.CREATE), entity)`; same shape for `@PostUpdate` (fallback `UPDATE`) and `@PostRemove` (fallback `DELETE`); Javadoc on each callback in [src/main/java/com/att/tdp/issueflow/auditlog/AuditableEntityListener.java](src/main/java/com/att/tdp/issueflow/auditlog/AuditableEntityListener.java)
- [ ] T018 [US1] [P] Add `@EntityListeners(AuditableEntityListener.class)` to [src/main/java/com/att/tdp/issueflow/user/User.java](src/main/java/com/att/tdp/issueflow/user/User.java) (add the corresponding import)
- [ ] T019 [US1] [P] Add `@EntityListeners(AuditableEntityListener.class)` to [src/main/java/com/att/tdp/issueflow/project/Project.java](src/main/java/com/att/tdp/issueflow/project/Project.java) (add the corresponding import)
- [ ] T020 [US1] [P] Add `@EntityListeners(AuditableEntityListener.class)` to [src/main/java/com/att/tdp/issueflow/ticket/Ticket.java](src/main/java/com/att/tdp/issueflow/ticket/Ticket.java) (add the corresponding import)
- [ ] T021 [US1] Extend the existing `@EntityListeners(AuditingEntityListener.class)` on [src/main/java/com/att/tdp/issueflow/comment/Comment.java](src/main/java/com/att/tdp/issueflow/comment/Comment.java) to `@EntityListeners({AuditingEntityListener.class, AuditableEntityListener.class})` (do NOT replace — keep `@CreatedDate`/`@LastModifiedDate` behaviour working)
- [ ] T022 [US1] Add `AuditContext.hint(AuditAction.SOFT_DELETE);` immediately before `projectRepository.save(project);` inside `softDelete(Long)` in [src/main/java/com/att/tdp/issueflow/project/ProjectService.java](src/main/java/com/att/tdp/issueflow/project/ProjectService.java) (add the import for `AuditContext` and `AuditAction`)
- [ ] T023 [US1] Add `AuditContext.hint(AuditAction.SOFT_DELETE);` immediately before `ticketRepository.save(ticket);` inside `softDelete(Long)` in [src/main/java/com/att/tdp/issueflow/ticket/TicketService.java](src/main/java/com/att/tdp/issueflow/ticket/TicketService.java) (add the imports)
- [ ] T024 [US1] Add `AuditContext.hint(AuditAction.STATUS_CHANGE);` inside the `if (request.status() != null)` branch (right after `ticket.setStatus(request.status());`) in `update(Long, UpdateTicketRequest)` in [src/main/java/com/att/tdp/issueflow/ticket/TicketService.java](src/main/java/com/att/tdp/issueflow/ticket/TicketService.java) — this overrides the default `UPDATE` action for status-bearing updates; document with a one-line `// status changes are audited as STATUS_CHANGE; see auditlog package` comment

**Checkpoint**: Listener attached to all four entities; service hints record semantic actions; unit tests pass. Audit entries are written to the database but cannot yet be read via API (no controller exists yet — that is US2). To validate manually, run the app, perform a write, and `SELECT * FROM audit_logs` in Adminer.

---

## Phase 4: User Story 2 — Read the Audit Log (Priority: P1) 🎯 MVP-half

**Goal**: An administrator can issue `GET /audit-logs` and receive a JSON array of all entries, newest first. The endpoint is gated to `ADMIN`-only; non-admins get 403; unauthenticated callers get 401.

**Independent Test**: `GET /audit-logs` as admin → `200 OK` with a JSON array sorted by `timestamp` desc (per spec scenarios 1 and 2). As a `DEVELOPER` → `403`. Unauthenticated → `401`. Combined with US1, the smoke flow in [quickstart.md](quickstart.md) round-trips: admin creates a project, then admin reads `/audit-logs` and finds the matching entry.

### Tests for User Story 2

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T025 [US2] Add failing test `findAllReturnsEntriesSortedByTimestampDescending` to [src/test/java/com/att/tdp/issueflow/auditlog/AuditLogServiceTest.java](src/test/java/com/att/tdp/issueflow/auditlog/AuditLogServiceTest.java) — stub `auditLogRepository.findAll(any(Sort.class))` to return three `AuditLog` rows; assert the call passed `Sort.by(Sort.Direction.DESC, "timestamp")` and the service mapped the result to `List<AuditLogResponse>` in the same order
- [ ] T026 [US2] [P] Create `AuditLogControllerAuthorizationTest` using standalone `MockMvcBuilders.standaloneSetup(controller).apply(SecurityMockMvcConfigurers.springSecurity(...))` (or `WebTestClient` with `@WithMockUser` if simpler — pure-JVM, no `@SpringBootTest`); failing tests `returnsTwoHundredForAdmin` (`@WithMockUser(authorities="ADMIN")`), `returnsForbiddenForDeveloper` (`@WithMockUser(authorities="DEVELOPER")`), `returnsUnauthorizedForUnauthenticated` — note: the 401 case may need a thin `@AutoConfigureMockMvc(addFilters=true) @WebMvcTest(AuditLogController.class)` slice; if a Spring slice test is required here, document it as the one accepted exception to "no Spring context" because security-filter behaviour cannot be unit-tested any other way in [src/test/java/com/att/tdp/issueflow/auditlog/AuditLogControllerAuthorizationTest.java](src/test/java/com/att/tdp/issueflow/auditlog/AuditLogControllerAuthorizationTest.java)

### Implementation for User Story 2

- [ ] T027 [US2] [P] Create `AuditLogResponse` record with fields `Long id`, `AuditAction action`, `AuditEntityType entityType`, `Long entityId`, `Long performedBy`, `ActorKind actor`, `Instant timestamp` (enums serialise as strings by default) in [src/main/java/com/att/tdp/issueflow/auditlog/dto/AuditLogResponse.java](src/main/java/com/att/tdp/issueflow/auditlog/dto/AuditLogResponse.java)
- [ ] T028 [US2] [P] Create `AuditLogMapper` (`@Mapper(componentModel = "spring")`) with `AuditLogResponse toResponse(AuditLog entity)` — explicitly `@Mapping(target = "details", ignore = true)` to satisfy `unmappedTargetPolicy=ERROR`; also `List<AuditLogResponse> toResponseList(List<AuditLog>)` in [src/main/java/com/att/tdp/issueflow/auditlog/AuditLogMapper.java](src/main/java/com/att/tdp/issueflow/auditlog/AuditLogMapper.java)
- [ ] T029 [US2] Add `@Transactional(readOnly = true) public List<AuditLogResponse> findAll()` to `AuditLogService` — calls `auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"))` and maps via `auditLogMapper.toResponseList(...)`; inject `AuditLogMapper` in the constructor in [src/main/java/com/att/tdp/issueflow/auditlog/AuditLogService.java](src/main/java/com/att/tdp/issueflow/auditlog/AuditLogService.java)
- [ ] T030 [US2] Create `AuditLogController` (`@RestController @RequestMapping("/audit-logs")`) with a single `@GetMapping @PreAuthorize("hasAuthority('ADMIN')") @ResponseStatus(HttpStatus.OK) public List<AuditLogResponse> list()` calling `auditLogService.findAll()`; Javadoc explaining the absence of write mappings is the FR-010 enforcement; constructor injection in [src/main/java/com/att/tdp/issueflow/auditlog/AuditLogController.java](src/main/java/com/att/tdp/issueflow/auditlog/AuditLogController.java)
- [ ] T031 [US2] Create the Postman collection skeleton with: pre-request that POSTs `/auth/login` with admin credentials and saves the token to a `{{adminToken}}` collection variable; a parallel block for `{{developerToken}}`; first request: `GET /audit-logs` with `{{adminToken}}` → expects `200`, JSON array, sorted by `timestamp` desc (test script asserts `response.length > 0` and `parseInt(...)` timestamps are non-ascending); second request: `GET /audit-logs` with `{{developerToken}}` → expects `403`; third request: `GET /audit-logs` with no Authorization header → expects `401` in [postman/audit-logs.postman_collection.json](postman/audit-logs.postman_collection.json)

**Checkpoint**: `GET /audit-logs` works end-to-end. Combined with US1 this is the MVP: any audited write produces a row that an admin can list.

---

## Phase 5: User Story 3 — Filter the Audit Log (Priority: P2)

**Goal**: An administrator narrows the audit log via any subset of `entityType`, `entityId`, `action`, `actor`. Filters combine with AND. `entityId` without `entityType` returns `400` (entity ids are not globally unique across types). Unknown enum values also return `400`.

**Independent Test**: `GET /audit-logs?entityType=TICKET&action=DELETE` returns only entries matching both. `GET /audit-logs?entityId=5` (no `entityType`) returns `400 Bad Request`. `GET /audit-logs?entityType=BANANA` returns `400 Bad Request`.

### Tests for User Story 3

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T032 [US3] Add failing tests `findAllAppliesEntityTypeFilter`, `findAllAppliesAllFourFiltersCombinedWithAnd`, `findAllReturnsEmptyListWhenFiltersMatchNothing`, `validateThrowsWhenEntityIdSuppliedWithoutEntityType` to [src/test/java/com/att/tdp/issueflow/auditlog/AuditLogServiceTest.java](src/test/java/com/att/tdp/issueflow/auditlog/AuditLogServiceTest.java) — use `ArgumentCaptor<Specification<AuditLog>>` to capture and assert the specification structure (or verify the result through a Mockito `Answer` that builds an in-memory predicate)
- [ ] T033 [US3] [P] Add `AuditLogControllerFilterValidationTest` with tests `rejectsEntityIdWithoutEntityTypeWith400` and `rejectsUnknownEntityTypeEnumWith400` using the standalone MockMvc setup from T026 (with `@WithMockUser(authorities="ADMIN")`) in [src/test/java/com/att/tdp/issueflow/auditlog/AuditLogControllerFilterValidationTest.java](src/test/java/com/att/tdp/issueflow/auditlog/AuditLogControllerFilterValidationTest.java)

### Implementation for User Story 3

- [ ] T034 [US3] [P] Create `AuditLogFilter` record `(AuditEntityType entityType, Long entityId, AuditAction action, ActorKind actor)` with `public void validate()` throwing `InvalidFilterException("entityId requires entityType to be specified")` when `entityId != null && entityType == null` in [src/main/java/com/att/tdp/issueflow/auditlog/dto/AuditLogFilter.java](src/main/java/com/att/tdp/issueflow/auditlog/dto/AuditLogFilter.java)
- [ ] T035 [US3] [P] Create `InvalidFilterException extends RuntimeException` accepting a message in [src/main/java/com/att/tdp/issueflow/common/error/InvalidFilterException.java](src/main/java/com/att/tdp/issueflow/common/error/InvalidFilterException.java)
- [ ] T036 [US3] Add `@ExceptionHandler(InvalidFilterException.class)` returning `400 Bad Request` via `ProblemDetailFactory.malformedRequest(exception.getMessage())` AND `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` returning `400 Bad Request` via `ProblemDetailFactory.malformedRequest("Invalid value '" + exception.getValue() + "' for parameter '" + exception.getName() + "'")` to [src/main/java/com/att/tdp/issueflow/common/error/GlobalExceptionHandler.java](src/main/java/com/att/tdp/issueflow/common/error/GlobalExceptionHandler.java) (add the imports)
- [ ] T037 [US3] Replace the no-arg `findAll()` on `AuditLogService` with `public List<AuditLogResponse> findAll(AuditLogFilter filter)`: call `filter.validate()` first; build a `Specification<AuditLog>` via `Specification.where(null)` then `.and(eq("entityType", ...))` (etc.) for each non-null filter field; call `auditLogRepository.findAll(spec, Sort.by(DESC, "timestamp"))`; map and return in [src/main/java/com/att/tdp/issueflow/auditlog/AuditLogService.java](src/main/java/com/att/tdp/issueflow/auditlog/AuditLogService.java)
- [ ] T038 [US3] Update `AuditLogController.list(...)` to declare `@RequestParam(required=false) AuditEntityType entityType, @RequestParam(required=false) Long entityId, @RequestParam(required=false) AuditAction action, @RequestParam(required=false) ActorKind actor`; construct `new AuditLogFilter(entityType, entityId, action, actor)` and pass to the service in [src/main/java/com/att/tdp/issueflow/auditlog/AuditLogController.java](src/main/java/com/att/tdp/issueflow/auditlog/AuditLogController.java)
- [ ] T039 [US3] Extend the Postman collection with: `GET /audit-logs?entityType=TICKET` (asserts every returned `entityType === 'TICKET'`); `GET /audit-logs?entityType=TICKET&entityId={{ticketId}}` (asserts every `entityId === {{ticketId}}`); `GET /audit-logs?entityType=TICKET&entityId=5&action=UPDATE&actor=USER` (all four filters); `GET /audit-logs?entityId=5` → expects `400` with problem-detail body; `GET /audit-logs?entityType=BANANA` → expects `400`; `GET /audit-logs?entityType=TICKET&entityId=999999999` (no matches) → expects `200` with empty array (not 404) in [postman/audit-logs.postman_collection.json](postman/audit-logs.postman_collection.json)

**Checkpoint**: All four filters work; bad enum / orphan `entityId` → 400; non-matching filter → empty array (not 404). Stories 1, 2, 3 independently functional.

---

## Phase 6: User Story 4 — Immutability Guarantee (Priority: P3)

**Goal**: Prove the audit log is read-only through the API. No `POST`, `PUT`, `PATCH`, or `DELETE` mapping exists on `/audit-logs`. The entity's `updatable = false` annotations on every audit column form a second layer at the JPA boundary.

**Independent Test**: Negative — `POST /audit-logs`, `PATCH /audit-logs/1`, `PUT /audit-logs/1`, `DELETE /audit-logs/1` all return `405 Method Not Allowed` (since no such routes are defined) or `404 Not Found` (for the `/{id}` paths if Spring doesn't synthesise the 405). The entries already present remain after any deletion of the underlying entity they describe (per FR-011).

### Tests for User Story 4

- [ ] T040 [US4] Extend the Postman collection with four negative cases as admin: `POST /audit-logs` → expects `405`; `PATCH /audit-logs/1` → expects `405` or `404`; `PUT /audit-logs/1` → expects `405` or `404`; `DELETE /audit-logs/1` → expects `405` or `404`; AND one positive case proving entry persistence after target deletion: admin creates a project, captures its id, soft-deletes the project, then `GET /audit-logs?entityType=PROJECT&entityId={{projectId}}` returns both the CREATE and SOFT_DELETE entries (asserts `length === 2`) in [postman/audit-logs.postman_collection.json](postman/audit-logs.postman_collection.json)

### Implementation for User Story 4

(No implementation tasks — FR-010 is enforced by absence: the controller from T030 has only `@GetMapping`. T006 enforces FR-013 via `updatable = false` on every audit column. This phase is purely the verification that the absence is intentional and complete.)

- [ ] T041 [US4] Self-review checklist task: confirm `AuditLogController` contains no `@PostMapping`, `@PutMapping`, `@PatchMapping`, or `@DeleteMapping`; confirm `AuditLog` entity columns `action`, `entityType`, `entityId`, `performedBy`, `actor`, `timestamp` all carry `updatable = false`; record findings as a one-line note in [specs/007-audit-log/checklists/requirements.md](specs/007-audit-log/checklists/requirements.md) under "Notes"

**Checkpoint**: All four stories independently verified. Feature is complete.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Formatting, full-build verification, AI-usage record, and end-to-end smoke run.

- [ ] T042 [P] Add a `prompts.md` entry summarising the `/speckit-specify` → `/speckit-plan` → `/speckit-tasks` cycle for the audit-log feature — model name (Opus 4.7), three key prompts, and the README-canonical reconciliation decision in [prompts.md](prompts.md) (create file if it does not exist; otherwise append a new section)
- [ ] T043 Run `./mvnw spotless:apply` from the repo root, then `./mvnw clean verify` and ensure both unit tests AND Spotless compile-phase check pass
- [ ] T044 Run `npx newman run postman/audit-logs.postman_collection.json -e postman/issueflow.local.postman_environment.json` against a freshly-started local app (`docker compose up -d` + `./mvnw spring-boot:run`) and confirm all assertions pass
- [ ] T045 Manually walk through the smoke flow in [specs/007-audit-log/quickstart.md](specs/007-audit-log/quickstart.md) (admin login → POST a project → GET /audit-logs?entityType=PROJECT&action=CREATE) and confirm at least one matching entry appears
- [ ] T046 No README change is required — the [README.md](README.md) "Audit Log APIs" row already matches the implemented contract. Confirm in a comment on the PR description rather than editing the file
- [ ] T047 `run.md` requires no changes — confirm no new env vars, ports, or startup steps were introduced; document confirmation in the PR description

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS every story phase
- **US1 (Phase 3)**: Depends on Phase 2 — does NOT depend on US2
- **US2 (Phase 4)**: Depends on Phase 2 — does NOT logically depend on US1, but the Postman smoke in T031 is more meaningful once US1 also exists (so the list returns something). The code paths are independent — US2 can be written and unit-tested in isolation.
- **US3 (Phase 5)**: Depends on Phase 2 AND Phase 4 (extends the controller and service introduced in Phase 4)
- **US4 (Phase 6)**: Depends on Phase 4 (asserts properties of the controller created in T030) and on US1 + US3 for the entry-persistence positive case
- **Polish (Phase 7)**: Depends on all desired story phases being complete

### Within Each Story

1. Write tests → confirm they FAIL
2. Implement entity / service method / controller mapping
3. Confirm tests PASS
4. Postman task last (acts as wire-level smoke)

### Parallel Opportunities

- T003, T004, T005 (three enum files) — run together
- T008, T009, T010, T011 (four utility classes in different packages) — run together with the enums
- T012, T013 (two test files for utilities) — run together once the utilities exist
- T018, T019, T020 (three @EntityListeners additions on different entity files) — run together once T017 exists
- T027, T028 (DTO record + MapStruct mapper in same package, different files) — run together
- T034, T035 (filter record + new exception class in different packages) — run together
- T042 (prompts.md edit) is independent of code work and can be done at any point after Phase 4

### Sequential Constraints

- T002 (migration) must complete before T006 (entity) so Hibernate validates the mapping against the real table on startup
- T017 (listener class) must exist before T018–T021 reference it
- T016 (`record()` method) must exist before T017 looks it up at runtime; the implementations can be authored in parallel but T017 will throw at runtime without T016
- T029 (`findAll()`) must exist before T030 (controller calls it)
- T037 (filter-aware `findAll(filter)`) replaces the signature from T029 — coordinate so the controller in T030/T038 is updated atomically
- T031 (Postman GET assertions) must be authored after T030 ships the endpoint
- T039 (Postman filter assertions) must be authored after T038 ships the query-param binding
- T040 (Postman immutability) must be authored after T038 (uses /audit-logs?entityType=PROJECT for the persistence assertion)

---

## Parallel Example: Phase 2 Foundational

```text
# After T002 (migration) lands, these eight tasks edit only new files — run together:
T003: AuditAction.java             (new enum)
T004: AuditEntityType.java         (new enum)
T005: ActorKind.java               (new enum)
T008: AuditContext.java            (new utility)
T009: SecurityUtil.java            (new utility)
T010: SpringContextHolder.java     (new bean)
T011: EntityClassifier.java        (new utility)

# Then in sequence:
T006: AuditLog.java                (depends on T003/T004/T005)
T007: AuditLogRepository.java      (depends on T006)
T012: SecurityUtilTest.java        (depends on T009)
T013: AuditContextTest.java        (depends on T008)
```

## Parallel Example: Phase 3 Listener Wiring

```text
# After T017 (the listener class) lands, these three @EntityListeners additions are
# independent edits to different entity files:
T018: User.java       — add @EntityListeners(AuditableEntityListener.class)
T019: Project.java    — add @EntityListeners(AuditableEntityListener.class)
T020: Ticket.java     — add @EntityListeners(AuditableEntityListener.class)

# T021 (Comment.java) is sequential because it edits an existing @EntityListeners array
# (must be merged into the existing AuditingEntityListener.class).
```

---

## Implementation Strategy

### MVP First (US1 + US2 together)

The spec marks both US1 and US2 as P1 because either alone is invisible. The MVP is therefore:

1. Phase 1 (Setup)
2. Phase 2 (Foundational)
3. Phase 3 (US1) — entries are written
4. Phase 4 (US2) — entries are readable as a plain JSON array
5. **STOP and VALIDATE**: walk the quickstart smoke flow — admin POSTs a project, then `GET /audit-logs` shows the CREATE entry
6. Demo / ship

### Incremental Delivery

1. Setup + Foundational → schema, entity, helpers ready, nothing observable yet
2. US1 → entries written; observable only via SQL
3. US2 → entries observable via API (MVP!)
4. US3 → filters
5. US4 → negative-test sweep
6. Polish → Spotless, full build, Newman, prompts.md

### Parallel Team Strategy

After Phase 2 completes:
- Developer A: US1 (Phases 3 — listener + service.record() + entity wiring + service hints)
- Developer B: US2 (Phase 4 — DTO + mapper + service.findAll() + controller + auth tests)
- Developer C: idles or works on Polish (T042 prompts.md is independent)

Then sequentially:
- US3 (single developer, after US2 ships)
- US4 (single developer, after US3 ships)

---

## Notes

- `[P]` tasks touch different files and have no incomplete dependencies
- `[USn]` label maps each task to a specific user story
- The endpoint returns a plain JSON array (NOT `PagedResponse`) — README is canonical per Constitution Principle IV (see plan.md Complexity Tracking and research.md Decision 4)
- Query params are exactly `entityType, entityId, action, actor` — no `performedBy` filter (research.md Decision 5)
- `details` JSONB column exists but is `null` in v1 (research.md Decision 7)
- `actor=SYSTEM` / `performedBy=null` is only reached when no authentication is present in the SecurityContext; in v1 this only happens during background bootstrap (e.g., `DevDataInitializer` writing seed data before the app accepts traffic). Later phases (auto-assignment, auto-escalation scheduler) will hit this path intentionally
- `AuditContext.hint(...)` is one-shot per save — set it immediately before `repository.save(...)` to avoid leaking between requests on pooled Tomcat threads
- Constitution Principle II requires both unit AND Postman tests for complex features. Both layers are present here — do not strip either at review time
- Spotless runs in Maven's `compile` phase, so an unformatted file fails `./mvnw verify`. Run `./mvnw spotless:apply` before pushing (T043)
