# Tasks: Projects — Create, Read, Update, Soft-Delete

**Input**: Design documents from `specs/004-projects-crud/`
**Prerequisites**: plan.md ✅ spec.md ✅ research.md ✅ data-model.md ✅ contracts/ ✅

**Tests**: Included — required by the Constitution (JUnit 5 + Postman mandatory for every feature).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)

---

## Phase 1: Setup (Migration)

**Purpose**: Establish the database schema. Blocks all user stories.

- [X] T001 Create Flyway migration `src/main/resources/db/migration/V5__projects.sql` — `projects` table with `id`, `name`, `description`, `owner_id`, `created_at`, `updated_at`, `deleted_at`; add partial unique index `ux_projects_name_active ON projects (LOWER(name)) WHERE deleted_at IS NULL`

---

## Phase 2: Foundational (Core Building Blocks)

**Purpose**: Entity, repository, DTOs, and mapper that every user story depends on. Must be complete before any story begins.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 Create `src/main/java/com/att/tdp/issueflow/project/Project.java` — `@Entity` with `id`, `name`, `description`, `@ManyToOne(fetch=LAZY) User owner`, `createdAt`, `updatedAt`, `deletedAt`; `@PrePersist`/`@PreUpdate` lifecycle hooks; Lombok `@Getter @Setter @NoArgsConstructor`; Javadoc on class
- [X] T003 [P] Create `src/main/java/com/att/tdp/issueflow/project/dto/ProjectResponse.java` — record with `Long id`, `String name`, `String description`, `Long ownerId`; Javadoc matching README response shape
- [X] T004 [P] Create `src/main/java/com/att/tdp/issueflow/project/dto/CreateProjectRequest.java` — record with `@NotBlank @Size(max=255) String name`, `String description`, `@NotNull Long ownerId`; Javadoc on each parameter
- [X] T005 [P] Create `src/main/java/com/att/tdp/issueflow/project/dto/UpdateProjectRequest.java` — record with nullable `String name`, nullable `String description`, `@AssertTrue` validation requiring at least one non-null; Javadoc
- [X] T006 Create `src/main/java/com/att/tdp/issueflow/project/ProjectRepository.java` — extends `JpaRepository<Project, Long>`; add derived queries: `findByIdAndDeletedAtIsNull(Long)`, `findAllByDeletedAtIsNullOrderByIdAsc()`, `existsByNameIgnoreCaseAndDeletedAtIsNull(String)`; Javadoc on each method (depends on T002)
- [X] T007 Create `src/main/java/com/att/tdp/issueflow/project/ProjectMapper.java` — `@Mapper` interface; `toEntity(CreateProjectRequest request, User owner)` with `@Mapping(target="id", ignore=true)`, `@Mapping(target="createdAt", ignore=true)`, `@Mapping(target="updatedAt", ignore=true)`, `@Mapping(target="deletedAt", ignore=true)`, `@Mapping(target="owner", source="owner")`; `toResponse(Project)` with `@Mapping(target="ownerId", source="owner.id")`; Javadoc on both methods (depends on T002, T003, T004)

**Checkpoint**: Entity, repository, DTOs, and mapper in place — user story implementation can begin.

---

## Phase 3: User Story 1 — Create a Project (Priority: P1) 🎯 MVP

**Goal**: A caller can `POST /projects` with name, description, and ownerId and receive a `200 OK` with the created project.

**Independent Test**: `POST /projects` with valid body → 200 with id; `GET /projects/{id}` (Phase 4) confirms persistence. Validate 400 on missing name, 404 on unknown ownerId, 409 on duplicate name.

### Implementation for User Story 1

- [X] T008 [US1] Create `src/main/java/com/att/tdp/issueflow/project/ProjectService.java` — `@Service`; inject `ProjectRepository` and `UserRepository` and `ProjectMapper`; implement `create(CreateProjectRequest)`: resolve owner via `userRepository.findById` (throw `NotFoundException` if absent), check `existsByNameIgnoreCaseAndDeletedAtIsNull` (throw `DuplicateResourceException` if true), map to entity via `projectMapper.toEntity(request, owner)`, save, return `projectMapper.toResponse(saved)`; `@Transactional`; Javadoc (depends on T006, T007)
- [X] T009 [US1] Create `src/main/java/com/att/tdp/issueflow/project/ProjectController.java` — `@RestController @RequestMapping("/projects")`; inject `ProjectService`; add `@PostMapping` `create(@Valid @RequestBody CreateProjectRequest)` returning `ProjectResponse` with `@ResponseStatus(HttpStatus.OK)`; Javadoc (depends on T008)

### Tests for User Story 1

- [X] T010 [P] [US1] Create `src/test/java/com/att/tdp/issueflow/project/ProjectServiceTest.java` — pure-JVM Mockito test class; implement `createProject_persistsAndReturnsResponse`, `createProject_throwsDuplicateWhenNameExists`, `createProject_throwsNotFoundWhenOwnerAbsent` (Arrange-Act-Assert; test names as sentences) (depends on T008)
- [X] T011 [P] [US1] Add "Projects" folder to `postman/issueflow.postman_collection.json` — requests: (1) Create project valid → 200 with body, (2) Create project missing name → 400, (3) Create project unknown ownerId → 404, (4) Create project duplicate name → 409 (depends on T009)

**Checkpoint**: `POST /projects` fully functional and tested. MVP achieved.

---

## Phase 4: User Story 2 — Fetch and List Projects (Priority: P1)

**Goal**: A caller can `GET /projects` to list all active projects and `GET /projects/{projectId}` to fetch one. Soft-deleted projects are invisible.

**Independent Test**: After creating a project (US1), `GET /projects` includes it. `GET /projects/{id}` returns it. Soft-delete one and re-fetch — both endpoints exclude it.

### Implementation for User Story 2

- [X] T012 [US2] Add `getById(Long id)` and `list()` to `src/main/java/com/att/tdp/issueflow/project/ProjectService.java` — `getById`: `findByIdAndDeletedAtIsNull(id)` → `NotFoundException` if empty → `toResponse`; `list`: `findAllByDeletedAtIsNullOrderByIdAsc()` → map each to response; `@Transactional(readOnly=true)` on both; Javadoc (depends on T008)
- [X] T013 [US2] Add `GET /projects` and `GET /projects/{projectId}` to `src/main/java/com/att/tdp/issueflow/project/ProjectController.java` — `@GetMapping` `list()` returning `List<ProjectResponse>`; `@GetMapping("/{projectId}")` `getById(@PathVariable Long projectId)` returning `ProjectResponse`; both `@ResponseStatus(OK)`; Javadoc (depends on T012)

### Tests for User Story 2

- [X] T014 [P] [US2] Add to `src/test/java/com/att/tdp/issueflow/project/ProjectServiceTest.java` — `getById_returnsProjectWhenActive`, `getById_throwsNotFoundWhenSoftDeleted`, `list_excludesSoftDeletedProjects` (depends on T012)
- [X] T015 [P] [US2] Add to Postman Projects folder in `postman/issueflow.postman_collection.json` — requests: (5) List projects → 200 array, (6) Get project by ID active → 200, (7) Get soft-deleted project → 404, (8) No auth → 401 (depends on T013)

**Checkpoint**: Read and list endpoints functional. Soft-delete visibility confirmed.

---

## Phase 5: User Story 3 — Update a Project (Priority: P2)

**Goal**: A caller can `PATCH /projects/{projectId}` with new name and/or description and receive `200 OK` with no body.

**Independent Test**: Create project, `PATCH` with new name → 200 empty → `GET /projects/{id}` reflects change. Verify 400 on empty body `{}`, 404 on deleted/missing project.

### Implementation for User Story 3

- [X] T016 [US3] Add `update(Long id, UpdateProjectRequest)` to `src/main/java/com/att/tdp/issueflow/project/ProjectService.java` — `findByIdAndDeletedAtIsNull(id)` → `NotFoundException` if absent; apply non-null `name` and `description` fields; `save`; return `void`; `@Transactional`; Javadoc (depends on T008)
- [X] T017 [US3] Add `PATCH /projects/{projectId}` to `src/main/java/com/att/tdp/issueflow/project/ProjectController.java` — `@PatchMapping("/{projectId}")` `update(@PathVariable Long projectId, @Valid @RequestBody UpdateProjectRequest)` returning `void`; `@ResponseStatus(OK)`; Javadoc (depends on T016)

### Tests for User Story 3

- [X] T018 [P] [US3] Add to `src/test/java/com/att/tdp/issueflow/project/ProjectServiceTest.java` — `update_appliesNameChange`, `update_throwsNotFoundWhenSoftDeleted` (depends on T016)
- [X] T019 [P] [US3] Add to Postman Projects folder in `postman/issueflow.postman_collection.json` — requests: (9) Update project valid name → 200 empty, (10) Update project empty body → 400, (11) Update soft-deleted project → 404 (depends on T017)

**Checkpoint**: Update endpoint functional and tested independently.

---

## Phase 6: User Story 4 — Soft-Delete a Project (Priority: P2)

**Goal**: A caller can `DELETE /projects/{projectId}` to soft-delete a project. Subsequent reads return `404`. Calling delete twice returns `404` on the second call.

**Independent Test**: Create project → `DELETE` → 200 → `GET /projects` excludes it → `GET /projects/{id}` returns 404 → second `DELETE` returns 404.

### Implementation for User Story 4

- [X] T020 [US4] Add `softDelete(Long id)` to `src/main/java/com/att/tdp/issueflow/project/ProjectService.java` — `findByIdAndDeletedAtIsNull(id)` → `NotFoundException` if absent or already deleted; `project.setDeletedAt(Instant.now())`; `save`; return `void`; `@Transactional`; Javadoc (depends on T008)
- [X] T021 [US4] Add `DELETE /projects/{projectId}` to `src/main/java/com/att/tdp/issueflow/project/ProjectController.java` — `@DeleteMapping("/{projectId}")` `softDelete(@PathVariable Long projectId)` returning `void`; `@ResponseStatus(OK)`; Javadoc (depends on T020)

### Tests for User Story 4

- [X] T022 [P] [US4] Add to `src/test/java/com/att/tdp/issueflow/project/ProjectServiceTest.java` — `softDelete_setsDeletedAt`, `softDelete_throwsNotFoundOnAlreadyDeleted` (depends on T020)
- [X] T023 [P] [US4] Add to Postman Projects folder in `postman/issueflow.postman_collection.json` — requests: (12) Delete project → 200 empty, (13) Delete same project again → 404, (14) Delete non-existent project → 404 (depends on T021)

**Checkpoint**: All four user stories complete and independently testable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, verification, final sign-off.

- [X] T024 [P] Add entry to `prompts.md` — record model and key prompts used during speckit-plan and speckit-tasks for this feature
- [X] T025 Run all quickstart smoke tests from `specs/004-projects-crud/quickstart.md` end-to-end against a running local instance; confirm every `curl` assertion matches expected output; fix any discrepancy before marking done

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on T001 (migration must exist for app to start)
- **Phase 3 (US1)**: Depends on Phase 2 complete — T008 depends on T006, T007; T009 depends on T008
- **Phase 4 (US2)**: Depends on T008 (extends existing service/controller)
- **Phase 5 (US3)**: Depends on T008
- **Phase 6 (US4)**: Depends on T008
- **Phase 7 (Polish)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2
- **US2 (P1)**: Can start after Phase 2 (extends same service/controller file as US1; sequence US1 → US2 to avoid merge conflicts on the same class files)
- **US3 (P2)**: Can start after US1/US2 (extends service/controller; safe to parallel if editing non-overlapping methods)
- **US4 (P2)**: Can start after US1/US2 (same reasoning as US3)

### Within Each User Story

- Service method before controller endpoint
- Controller endpoint before tests (tests require the method signature to be defined)
- Unit tests [P] and Postman additions [P] can run in parallel after the implementation task completes

---

## Parallel Example: Foundational Phase (Phase 2)

```
Parallel group A (can start together after T002 is done):
  T003 — ProjectResponse.java
  T004 — CreateProjectRequest.java
  T005 — UpdateProjectRequest.java

Sequential after A:
  T006 — ProjectRepository.java   (needs Project entity from T002)
  T007 — ProjectMapper.java       (needs entity + DTOs)
```

## Parallel Example: User Story 1 (Phase 3)

```
Sequential:
  T008 — ProjectService.create()
  T009 — ProjectController POST /projects

Parallel after T009:
  T010 — Unit tests (ProjectServiceTest create cases)
  T011 — Postman create requests
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Migration → Phase 2: Entity/Repo/DTOs/Mapper
2. Complete Phase 3: US1 (create endpoint + tests)
3. **STOP and VALIDATE**: `POST /projects` → 200, `400`, `404`, `409` all working
4. Demo the create endpoint independently

### Incremental Delivery

1. Phase 1 + 2 → Foundation ready
2. Phase 3 (US1) → Create working → Demo/test
3. Phase 4 (US2) → Read/List working → Demo/test
4. Phase 5 (US3) → Update working → Demo/test
5. Phase 6 (US4) → Soft-delete working → Demo/test
6. Phase 7 → Polish → Merge-ready

### Single-Developer Sequence

T001 → T002 → T003/T004/T005 (parallel) → T006 → T007 → T008 → T009 → T010/T011 (parallel) → T012 → T013 → T014/T015 (parallel) → T016 → T017 → T018/T019 (parallel) → T020 → T021 → T022/T023 (parallel) → T024/T025

---

## Notes

- [P] tasks operate on different files and have no unresolved dependencies — safe to parallelise
- All service methods use `findByIdAndDeletedAtIsNull` — never `findById` — to ensure soft-deleted projects are invisible
- Status code for every endpoint is `200 OK` — matches README canonical template; do NOT change to 201/204
- `ProjectMapper.toEntity` takes two parameters (`request` + resolved `User`) — not just the DTO — to keep the mapper free of repository calls
- MapStruct `unmappedTargetPolicy=ERROR` (set in `pom.xml`) means any new `Project` field must be explicitly mapped or ignored in `ProjectMapper` before the project will compile
- Total tasks: 25 | Tasks with [P]: 12 | Phases: 7
