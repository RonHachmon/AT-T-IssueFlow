# Research: Projects CRUD

**Branch**: `004-projects-crud` | **Date**: 2026-05-21

## Decision Log

### D-001: Response status codes — README templates override REST defaults

**Decision**: All five project endpoints return `200 OK` regardless of operation type.

**Rationale**: The `README.md` Projects APIs table is the canonical contract (Constitution Principle IV). It shows `200 OK` for every row — including `POST /projects` (which REST convention would put at `201 Created`) and `DELETE /projects/:projectId` (which would conventionally be `204 No Content`). The `UserController` already follows this same pattern (`@ResponseStatus(HttpStatus.OK)` on `POST` and `DELETE`). Deviating would break client consistency.

**Alternatives considered**: `201 Created` + `Location` header for POST; `204 No Content` for DELETE — both rejected because they contradict the README template.

---

### D-002: Soft-delete via `deleted_at` timestamp column

**Decision**: Add a nullable `deleted_at TIMESTAMPTZ` column to the `projects` table. A `NULL` value means active; a non-null value means soft-deleted. All standard repository queries filter `WHERE deleted_at IS NULL`.

**Rationale**: The spec requires deleted projects to be restorable in a later phase. A boolean `is_deleted` flag would work but loses the deletion timestamp (useful for auditing). A `deleted_at` column is idiomatic in Spring/JPA and matches the `created_at`/`updated_at` field-naming convention already on `User`.

**Alternatives considered**: Soft-delete via a status enum field — rejected, adds unnecessary complexity and diverges from the `created_at`/`updated_at` naming pattern.

---

### D-003: Project name uniqueness — partial index on active projects only

**Decision**: Enforce name uniqueness via a partial unique index `ON projects (LOWER(name)) WHERE deleted_at IS NULL`. This allows a deleted project's name to be reclaimed by a new project.

**Rationale**: Spec assumption: "Project name uniqueness is enforced across active projects only; a deleted project's name can be reused." A standard `UNIQUE` constraint would block reuse. A partial index on `deleted_at IS NULL` precisely enforces the right rule. Mirrors the approach used in `V2__users.sql` for `LOWER(username)`.

**Alternatives considered**: Application-layer uniqueness check only (no DB constraint) — rejected, leaves a race-condition window when two concurrent requests create the same name.

---

### D-004: Owner reference — `ManyToOne` to `User`, expose only `ownerId` in response

**Decision**: The `Project` entity has a `@ManyToOne(fetch = FetchType.LAZY)` to `User`. The `ProjectResponse` DTO carries only `ownerId` (a `Long`), matching the README response shape `{ "id": 1, "name": "...", "description": "...", "ownerId": 1 }`. The mapper derives `ownerId` from `project.getOwner().getId()` via `@Mapping(target = "ownerId", source = "owner.id")`.

**Rationale**: README response body shows a flat `ownerId` field, not a nested owner object. `LAZY` fetch prevents unnecessary user joins on reads. Resolving the `User` entity at write time (in the service) keeps the mapper simple.

**Alternatives considered**: Eager fetch + nested owner DTO — rejected, over-fetches and conflicts with the README shape.

---

### D-005: Update endpoint — `PATCH /projects/:projectId`, returns `200 OK` with no body

**Decision**: `updateProject` in the service updates `name` and/or `description` and saves. The controller returns `void` with `@ResponseStatus(HttpStatus.OK)`. At least one of the two fields must be non-null (enforced by Bean Validation `@AssertTrue`).

**Rationale**: README shows an empty response column for the PATCH endpoint, exactly as `POST /users/update/:userId` behaves. Owner change is explicitly out of scope (spec assumption).

**Alternatives considered**: Return updated project body from PATCH — rejected, README shows empty response.

---

### D-006: Soft-delete idempotency — treat already-deleted as 404

**Decision**: If `DELETE /projects/:projectId` is called on an already-deleted or non-existent project, the service throws `NotFoundException`. The client receives `404 Not Found`.

**Rationale**: Spec acceptance scenario 5 ("Already soft-deleted → 404"). This is intentional: the API surface treats deleted projects as invisible, so a second delete attempt is semantically identical to deleting a project that never existed.

**Alternatives considered**: `204 No Content` for idempotent double-delete — rejected, conflicts with the spec acceptance scenario.

---

### D-007: Flyway migration — V5__projects.sql

**Decision**: The next available migration version is `V5`. The migration creates the `projects` table and the partial unique index.

**Rationale**: Existing migrations are V1 through V4. V5 is the natural next slot. No other in-flight migration competes with this branch.

**Alternatives considered**: None — version numbering is mechanical.

---

### D-008: No pagination for project list

**Decision**: `GET /projects` returns a plain `List<ProjectResponse>`, not a paginated envelope.

**Rationale**: README shows a plain JSON array `[ { ... } ]` with no pagination wrapper. Spec assumption: "No pagination is required for the project list in this phase." Consistent with `GET /users` which also returns a plain array.

**Alternatives considered**: Paginated response — deferred to a future phase per spec assumption.
