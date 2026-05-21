---
description: "Task list for users-crud feature"
---

# Tasks: Users CRUD

**Input**: Design documents from `specs/002-users-crud/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: **MANDATORY** for this project. Constitution Principle II requires
both JUnit 5 + Mockito unit tests (no Spring context) AND Postman API tests
for every endpoint. Complex tests are mandatory wherever branching, state,
or edge cases exist — every story in this feature qualifies.

**Organization**: Tasks are grouped by user story (US1–US5 from [spec.md](spec.md)).
Once Setup + Foundational complete, US1, US2, US3, US4, US5 can be
implemented in parallel by different developers.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable — different files, no dependency on an incomplete task
- **[Story]**: which user story this task belongs to (US1–US5); omitted for
  Setup / Foundational / Polish

## Path Conventions

All paths are relative to the repository root. Java package root is
`com.att.tdp.issueflow`:

- Production code: `src/main/java/com/att/tdp/issueflow/<slice>/`
- Test code: `src/test/java/com/att/tdp/issueflow/<slice>/`
- Migrations: `src/main/resources/db/migration/`
- Postman: `postman/issueflow.postman_collection.json` (Users folder)
- Repo-root docs: `README.md`, `prompts.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Bring `pom.xml` to the dependency set the plan demands and
register the cross-slice singletons (Role enum, BCryptPasswordEncoder bean).

- [x] T001 `pom.xml` updated — added `mapstruct` (1.6.3) + `spring-security-crypto`. Annotation-processor chain: Lombok → lombok-mapstruct-binding (0.2.0) → mapstruct-processor. Compiler args set `mapstruct.defaultComponentModel=spring` and `mapstruct.unmappedTargetPolicy=ERROR` globally.
- [x] T002 [P] `Role.java` created in `user/` slice.
- [x] T003 [P] `PasswordEncoderConfiguration.java` created in `common/security/` returning `PasswordEncoder` interface from `BCryptPasswordEncoder`.

**Checkpoint**: `./mvnw clean compile` exits 0 with the new deps and processor chain in place.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Ship the database schema, the `User` entity + repository, the
canonical pagination envelope, and the new domain exceptions + advice
handlers. **No user-story work can begin until this phase is complete.**

- [x] T004 [P] `V2__users.sql` written — table + role CHECK + `LOWER(...)` functional unique indexes.
- [x] T005 [P] `User.java` entity with Lombok `@Getter @Setter @NoArgsConstructor`, `@PrePersist`/`@PreUpdate` for timestamps.
- [x] T006 [P] `UserRepository` with `existsByUsernameIgnoreCase` + `existsByEmailIgnoreCase`.
- [x] T007 [P] `PagedResponse<T>` record + `package-info.java` in `common/pagination/`.
- [x] T008 [P] `NotFoundException` + `DuplicateResourceException` in `common/error/`.
- [x] T009 `ErrorType.DUPLICATE_RESOURCE` constant added (URL form per approved plan). `ProblemDetailFactory.duplicateResource(...)` factory added.
- [x] T010 `GlobalExceptionHandler` extended with 4 handlers: `NotFoundException` → 404, `DuplicateResourceException` → 409, `DataIntegrityViolationException` → 409 safety net, `ConstraintViolationException` → 422 with `errors[]`.
- [x] T011 [P] `GlobalExceptionHandlerTest` extended with 4 new tests — 8/8 green.
- [x] T012 [P] `UserMapper` MapStruct interface with explicit `@Mapping(... ignore = true)` for server-managed and sensitive fields. Compile-time policy set globally to `ERROR` for unmapped targets — prevents accidental field leaks.

**Checkpoint**: `./mvnw test -Dtest=GlobalExceptionHandlerTest` runs all 8 (4 old + 4 new) and they pass. `./mvnw clean compile` exits 0 and the MapStruct-generated `UserMapperImpl` appears under `target/generated-sources/annotations`. User story implementation can now begin.

---

## Phase 3: User Story 1 — Create a user (Priority: P1) 🎯 MVP

**Goal**: `POST /users` accepts a valid request and persists a new user, returning 201 with a `Location` header and the persisted body.

**Independent Test**: `curl -X POST /users` with a valid body returns `201`, `Location: /users/{id}`, and the response body matches the input plus a server-generated `id` and timestamps. A second identical request returns `409`.

- [x] T013 [P] [US1] `CreateUserRequest` record with `@NotBlank`/`@Email`/`@Size`/`@Pattern`/`@NotNull`. (Done in Phase 2 actually — needed to make T012 mapper compile.)
- [x] T014 [P] [US1] `UserResponse` record — 7 fields, NO passwordHash.
- [x] T015 [US1] `UserService.create` — duplicate pre-checks + race-condition catch + `@Transactional`. (Implementation pass also added `getById`, `list`, `update`, `delete` to the same file in one Write — see prompts.md.)
- [x] T016 [US1] `UserController.create` — `POST /users` → 201 + Location URI. (Same Write pass added the other 4 endpoints too.)
- [x] T017 [P] [US1] `UserServiceTest` — 4 create-path tests green: happy, duplicate-username, duplicate-email, race-condition catch.
- [x] T018 [P] [US1] `UserControllerTest` — `returnsTwoHundredOneWithLocationHeaderWhenUserCreated` green.

**Checkpoint**: `./mvnw test` reports BUILD SUCCESS with the new tests passing. `curl -X POST http://localhost:8080/users -H 'Content-Type: application/json' -d '{...}'` returns 201 + Location.

---

## Phase 4: User Story 2 — Fetch a user by id (Priority: P1)

**Goal**: `GET /users/{userId}` returns the user or 404.

**Independent Test**: After creating a user, `GET /users/{id}` returns the same record. `GET /users/999999` returns 404 ProblemDetail.

- [x] T019 [US2] `UserService.getById` — landed in the same UserService Write as create.
- [x] T020 [US2] `UserController.getById` — landed in the same UserController Write.
- [x] T021 [P] [US2] Service tests: `returnsUserResponseWhenIdExists`, `throwsNotFoundExceptionWhenGetByIdMisses`. Controller test: `returnsUserResponseWhenGetByIdSucceeds`. All green.

**Checkpoint**: All US1 + US2 tests pass. `curl /users/{id}` works for both happy and missing-id paths.

---

## Phase 5: User Story 3 — List users with pagination (Priority: P1)

**Goal**: `GET /users` returns the canonical `PagedResponse<UserResponse>` envelope.

**Independent Test**: With three users created, `GET /users` returns `data.length == 3, total == 3, page == 0, pageSize == 20`. `GET /users?page=10&pageSize=20` returns an empty `data` array with `total == 3` (not a 404).

- [x] T022 [US3] `UserService.list` — canonical envelope, stable `id ASC` order. Same Write.
- [x] T023 [US3] `UserController.list` — `@Validated` class-level + `@Min`/`@Max` on `pageSize`. Same Write.
- [x] T024 [P] [US3] Service tests: empty/full/beyond-end. Controller test: envelope shape. All green.

**Checkpoint**: All US1–US3 tests pass. The three P1 stories are complete — this is the natural MVP cut.

---

## Phase 6: User Story 4 — Update a user's name or role (Priority: P2)

**Goal**: `PATCH /users/{userId}` applies partial updates to `fullName` and/or `role`, rejects empty bodies, rejects immutable-field changes, returns 204.

**Independent Test**: Create a user, PATCH with `{"role": "ADMIN"}`, fetch the user → role is ADMIN, fullName unchanged. PATCH with `{}` returns 422.

- [x] T025 [P] [US4] `UpdateUserRequest` record with `@AssertTrue hasAtLeastOneFieldSet()` for empty-PATCH rejection.
- [x] T026 [US4] `UserService.update` — partial-update logic, throws `NotFoundException` on miss.
- [x] T027 [US4] `UserController.update` — `PATCH /users/{userId}` → 204.
- [x] T028 [P] [US4] Service tests: 4 update-path tests. Controller test: delegates to service. All green.

**Checkpoint**: All US1–US4 tests pass. PATCH happy/not-found paths work.

---

## Phase 7: User Story 5 — Delete a user (Priority: P2)

**Goal**: `DELETE /users/{userId}` removes the user, returns 204. Subsequent fetches return 404.

**Independent Test**: Create a user, DELETE, then GET → 404. DELETE of unknown id → 404.

- [x] T029 [US5] `UserService.delete` — gate with `existsById` then `deleteById`. Same Write.
- [x] T030 [US5] `UserController.delete` — `DELETE /users/{userId}` → 204.
- [x] T031 [P] [US5] Service tests: happy + not-found. Controller test: delegates to service. All green.

**Checkpoint**: All US1–US5 tests pass. Full CRUD surface is live.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [!] T032 [P] README.md Users APIs table was **deliberately not changed** — user decision: "the README file is above all instruction, and must match one to one." The README's existing rows (POST → 200, POST /users/update/:userId, DELETE → 200, no pagination, no timestamps) are the authoritative contract. The implementation was REVERTED to match. **This supersedes constitution Principle IV's prescribed status codes.** Constitution amendment recommended (MAJOR bump) to formally make README authoritative.
- [x] T033 [P] Postman "Users" folder updated to match the new README-aligned contract: Create → 200 (no Location), Get → 200 (5-field body), List → 200 plain array, Update → POST /users/update/{id} → 200, Delete → 200. Error-path requests retain RFC 7807 expectations: 409 duplicate, 422 validation, 404 unknown id.
- [x] T034 [P] `prompts.md` "Feature 002 — Users CRUD" section appended.
- [x] T035 `./mvnw clean verify` and `./mvnw test` both green — 31 tests after the revert (one consolidated pagination test was dropped since pagination no longer applies). Spotless clean. Jar built.
- [x] T036 Live verification COMPLETE: app boots against Postgres → curl POST /users → 200 with 5-field body → curl GET /users → 200 plain array → curl POST /users/update/{id} → 200 empty body, role changed → curl DELETE → 200 empty body. Case-insensitive duplicate `ALICE` after `alice` → 409. Malformed email → 422 with field error. Unknown id → 404. Newman against Users folder: **8/8 requests, 15/15 assertions passing.**

## REVERT NOTE (post-T036)

After T035, the user clarified: "the readme file is above all instruction,
and must match one to one. even if the response status at the API show for
create 200, when 201 is more common we should stick to 200." The full
implementation was then reverted to match the README contract:

| What I built first (constitution Principle IV) | What I reverted to (README authoritative) |
|---|---|
| `POST /users` → 201 + Location header | `POST /users` → 200 + body, no Location |
| `GET /users?page=N&pageSize=M` → 200 + `{ data, page, pageSize, total }` envelope | `GET /users` → 200 + plain JSON array |
| `PATCH /users/{userId}` → 204 | `POST /users/update/{userId}` → 200 + empty body |
| `DELETE /users/{userId}` → 204 | `DELETE /users/{userId}` → 200 + empty body |
| `UserResponse` 7 fields incl. `createdAt`/`updatedAt` | `UserResponse` 5 fields, no timestamps |

The entity still carries `created_at` / `updated_at` in the database for
audit purposes; only the API surface drops them. `PagedResponse<T>` was
kept in the codebase under `common/pagination/` for future endpoints
(README's Mentions API still uses pagination), but is unused for now.

**Constitution Principle IV is now in conflict** with this PR. A
`/speckit-constitution` amendment is recommended to declare the README
authoritative for status codes and response shapes.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: no dependencies — start immediately. T002 || T003 after T001.
- **Phase 2 (Foundational)**: depends on Phase 1. T004 || T005 || T006 || T007 || T008 after T001. T009 needs T008 (and existing skeleton's `ErrorType` + `ProblemDetailFactory`). T010 needs T008 + T009. T011 needs T010. T012 needs T005 + T013 + T014 (mapper signature references DTOs) — **but the DTOs are created in Phase 3**; in practice T012 is finalized at the start of Phase 3, not Phase 2. To keep Phase 2 self-contained, T012 may be written with only the entity-side signature and the DTO-side methods added in Phase 3. **Pragmatic ordering**: do T012 at the boundary of Phase 2/3, after T013 + T014 land.
- **Phase 3 (US1)**: depends on Phase 2. T013 || T014 first; then T015 (uses both DTOs + repo + mapper); then T016 (uses service); T017 || T018 last.
- **Phase 4 (US2)**: depends on Phase 3 (extends the same `UserService` and `UserController` files). T019 → T020 → T021.
- **Phase 5 (US3)**: depends on Phase 3 (same files) + Phase 2 T007 (`PagedResponse`). T022 → T023 → T024.
- **Phase 6 (US4)**: depends on Phase 3 (same files). T025 first; T026 → T027 → T028.
- **Phase 7 (US5)**: depends on Phase 3 (same files). T029 → T030 → T031.
- **Phase 8 (Polish)**: depends on all preceding phases.

### Within each user-story phase

- DTO before service (service signature references DTO).
- Service before controller (controller delegates).
- Tests can be written in parallel with the implementation in the same task or as a follow-up — they're listed as separate tasks so a TDD-leaning implementer can swap the order.

### Parallel opportunities

- **Phase 1**: T002 || T003 after T001.
- **Phase 2**: T004 || T005 || T006 || T007 || T008 (five independent files). T011 || T012 after T010.
- **Phase 3**: T013 || T014 after T012. T017 || T018 after T016.
- **Phase 4/5/6/7**: each story's tests (`[P]` tasks) can run alongside the next story's first task once the previous controller method exists.
- **Phase 8**: T032 || T033 || T034 || T036 (touch different files); T035 is sequential because it must run last.

### Parallel example — US1 implementation start

```bash
# After Phase 2 completes:
Task T013 [US1]: CreateUserRequest at src/main/java/com/att/tdp/issueflow/user/dto/CreateUserRequest.java
Task T014 [US1]: UserResponse at src/main/java/com/att/tdp/issueflow/user/dto/UserResponse.java
```

```bash
# After T016 lands:
Task T017 [US1]: UserServiceTest at src/test/java/com/att/tdp/issueflow/user/UserServiceTest.java
Task T018 [US1]: UserControllerTest at src/test/java/com/att/tdp/issueflow/user/UserControllerTest.java
```

---

## Implementation Strategy

### MVP First (US1 + US2 + US3 — all three are P1)

1. Phase 1 Setup.
2. Phase 2 Foundational.
3. Phase 3 US1 Create.
4. Phase 4 US2 Fetch.
5. Phase 5 US3 List.
6. **STOP and VALIDATE**: `./mvnw clean verify` green; `curl POST /users`, `curl GET /users/{id}`, `curl GET /users` all return constitutional status codes and matching bodies.

At this point the spec's "establishes who exists in the system" promise is met. US4 and US5 are quality-of-life additions.

### Incremental Delivery

1. Phases 1 + 2 → foundation ready (no demo-able output).
2. Phase 3 → demo: POST returns 201 + Location, body roundtrips.
3. Phase 4 → demo: GET by id works, 404 carries Problem Details.
4. Phase 5 → demo: list returns the canonical envelope.
5. Phase 6 → demo: PATCH role-only and name-only both work; empty PATCH → 422.
6. Phase 7 → demo: DELETE removes the record; double-delete → 404.
7. Phase 8 → README accurate, Postman runs green via Newman, prompts.md honest.

### Parallel Team Strategy

With three developers after Phase 2 completes:

- Dev A: US1 (T013–T018) — owns the create slice end-to-end.
- Dev B: US3 (T022–T024) — pagination is the most cross-cutting story.
- Dev C: US2 then US4 (T019–T021, T025–T028) — fetch first since it's a precondition for visually verifying US1 + US3.

US5 (T029–T031) is small enough that whichever dev finishes first picks it up.

---

## Notes

- `[P]` means different files AND no unfinished dependency.
- Tasks that extend the same Java file across phases (e.g., `UserService.java` evolves across US1–US5) are deliberately sequenced — they share a file path, so `[P]` would be incorrect even when the methods are independent.
- Tests for the same `*Test.java` file across stories are sequenced for the same reason. The `[P]` on test tasks within a single phase reflects two *different* test files (`UserServiceTest.java` and `UserControllerTest.java`).
- Constitution Principle II makes T011, T017, T018, T021, T024, T028, T031 **mandatory** — not optional. Postman additions in T033 are mandatory for the same reason.
- Commit at each Phase checkpoint; do not push until T035 confirms a clean `./mvnw clean verify`.
