# Feature Specification: Projects — Create, Read, Update, Soft-Delete

**Feature Branch**: `004-projects-crud`
**Created**: 2026-05-21
**Status**: Draft
**Input**: User description: "Introduce projects — the containers that will later hold tickets. You can create a project with a name, description, and owner (a user). You can fetch one, list them all, update name/description, and delete one. Deleting is "soft" — the project disappears from normal listings but isn't truly gone, so it can be restored later (the restore endpoints themselves come in a dedicated phase)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create a Project (Priority: P1)

A user creates a new project by providing a name, optional description, and designating an owner (an existing user). The project is immediately visible in listings and available for fetching by ID.

**Why this priority**: Projects are the foundational container for all future work. Without the ability to create one, no other project operation or subsequent ticket feature is possible. This single endpoint delivers the core data model.

**Independent Test**: `POST /projects` with valid name and owner ID → `201 Created` with a project object. Then `GET /projects/{id}` returns the same project.

**Acceptance Scenarios**:

1. **Given** a valid user exists with ID 5, **When** `POST /projects` is called with `{ "name": "Backend Rewrite", "description": "Modernise the service layer", "ownerId": 5 }`, **Then** the response is `201 Created` with a project object containing a generated ID, the provided fields, and a `createdAt` timestamp.
2. **Given** a `name` is omitted, **When** `POST /projects` is called, **Then** the response is `400 Bad Request` with a validation error for the missing field.
3. **Given** the `ownerId` does not reference an existing user, **When** `POST /projects` is called, **Then** the response is `404 Not Found` with a descriptive error message.
4. **Given** a project with the same name already exists, **When** `POST /projects` is called with that name, **Then** the response is `409 Conflict`.

---

### User Story 2 - Fetch and List Projects (Priority: P1)

Users can retrieve a single project by its ID, or list all currently active (non-deleted) projects.

**Why this priority**: Reading data is the most frequent operation and is required for any UI or integration to display project information. Listing and fetching together confirm the data model and soft-delete behaviour are correct.

**Independent Test**: With at least one project created (Story 1), `GET /projects` returns it in a list. `GET /projects/{id}` returns it individually. A soft-deleted project does not appear in the list.

**Acceptance Scenarios**:

1. **Given** three active projects exist, **When** `GET /projects` is called, **Then** the response is `200 OK` with a list of three project objects.
2. **Given** a project with ID 7 exists, **When** `GET /projects/7` is called, **Then** the response is `200 OK` with that project's data.
3. **Given** no project with ID 999 exists, **When** `GET /projects/999` is called, **Then** the response is `404 Not Found`.
4. **Given** a project has been soft-deleted, **When** `GET /projects` is called, **Then** the deleted project does not appear in the list.
5. **Given** a soft-deleted project's ID, **When** `GET /projects/{id}` is called, **Then** the response is `404 Not Found` (deleted projects are invisible to normal reads).

---

### User Story 3 - Update a Project (Priority: P2)

Users can update a project's name and/or description. The owner cannot be changed through this endpoint (owner transfer is out of scope for this phase).

**Why this priority**: Updates are needed for day-to-day project management but are not a blocker for the core data model or read flows. Delivered after create/read are stable.

**Independent Test**: `PATCH /projects/{id}` with a new name → `200 OK` → `GET /projects/{id}` reflects the updated name.

**Acceptance Scenarios**:

1. **Given** a project with ID 3 exists, **When** `PATCH /projects/3` is called with `{ "name": "New Name" }`, **Then** the response is `200 OK` with the updated project object and an updated `updatedAt` timestamp.
2. **Given** a project with ID 3 exists, **When** `PATCH /projects/3` is called with `{ "description": "Updated desc" }`, **Then** only the description is changed; name remains unchanged.
3. **Given** a project does not exist, **When** `PATCH /projects/{id}` is called, **Then** the response is `404 Not Found`.
4. **Given** a soft-deleted project's ID, **When** `PATCH /projects/{id}` is called, **Then** the response is `404 Not Found`.
5. **Given** an empty body `{}`, **When** `PATCH /projects/{id}` is called, **Then** the response is `400 Bad Request` — at least one field must be provided.

---

### User Story 4 - Soft-Delete a Project (Priority: P2)

Deleting a project marks it as deleted without physically removing its data. It disappears from normal listings and single-fetch operations, but its data is preserved for future restoration (restore endpoints are a separate phase).

**Why this priority**: Soft-delete is the correct deletion semantics for the domain and must be implemented before listing/fetch can be confirmed correct. Depends on Stories 1 and 2.

**Independent Test**: Create a project, `DELETE /projects/{id}` → `204 No Content` → `GET /projects` no longer includes it → `GET /projects/{id}` returns `404`.

**Acceptance Scenarios**:

1. **Given** a project with ID 4 exists, **When** `DELETE /projects/4` is called, **Then** the response is `204 No Content`.
2. **Given** the project from scenario 1, **When** `GET /projects` is called, **Then** the project is absent from the list.
3. **Given** the project from scenario 1, **When** `GET /projects/4` is called, **Then** the response is `404 Not Found`.
4. **Given** a project that does not exist, **When** `DELETE /projects/{id}` is called, **Then** the response is `404 Not Found`.
5. **Given** an already soft-deleted project, **When** `DELETE /projects/{id}` is called again, **Then** the response is `404 Not Found` (idempotent at the API level).

---

### Edge Cases

- What happens when `name` is an empty string or only whitespace? → `400 Bad Request` with a validation error.
- What happens when `name` exceeds a reasonable length (e.g., 255 characters)? → `400 Bad Request`.
- What happens when `description` is omitted during creation? → Treated as null/empty; project is created successfully.
- What happens when two concurrent requests try to create a project with the same name? → One succeeds (`201`); the other receives `409 Conflict`.
- What happens when `GET /projects` is called with no projects yet? → `200 OK` with an empty list, not `404`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow creating a project with a required `name` (non-blank, max 255 characters), optional `description`, and a required `ownerId` referencing an existing user.
- **FR-002**: System MUST reject project creation if `name` is missing, blank, or exceeds 255 characters.
- **FR-003**: System MUST reject project creation if `ownerId` does not reference an existing user.
- **FR-004**: System MUST reject project creation if a project with the same `name` already exists (active projects only).
- **FR-005**: System MUST return a single project by its ID, excluding soft-deleted projects.
- **FR-006**: System MUST return all active (non-deleted) projects in a list; soft-deleted projects are excluded.
- **FR-007**: System MUST allow updating a project's `name` and/or `description`; at least one field must be supplied.
- **FR-008**: System MUST NOT allow changing the project owner via the update endpoint.
- **FR-009**: System MUST soft-delete a project by marking it as deleted rather than removing its record.
- **FR-010**: System MUST preserve all project data after soft-deletion for future restoration.
- **FR-011**: All endpoints MUST require a valid bearer token (per existing JWT auth).
- **FR-012**: System MUST record `createdAt` on creation and `updatedAt` on every modification.

### Key Entities

- **Project**: A named container for future tickets. Key attributes: `id` (generated), `name` (unique among active, required), `description` (optional), `owner` (reference to a User), `createdAt`, `updatedAt`, `deletedAt` (null = active, non-null = soft-deleted).
- **User** (existing): Referenced as the project owner by ID. No changes to the User entity in this phase.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can create, retrieve, update, and delete a project in under 30 seconds of total interaction time across the four operations.
- **SC-002**: All four CRUD endpoints respond within 200 ms under normal load (single-node, no concurrent stress).
- **SC-003**: 100% of soft-deleted projects are absent from list and single-fetch responses — zero leakage of deleted data to normal reads.
- **SC-004**: All validation errors return structured, human-readable messages sufficient for a UI to display without additional lookups.
- **SC-005**: The project data model is fully backward-compatible with zero changes to existing endpoints (users, auth).

## Assumptions

- The caller is always an authenticated user with a valid bearer token; no anonymous project access is permitted.
- Owner transfer (reassigning `ownerId`) is out of scope for this phase; only `name` and `description` are updatable.
- Restore endpoints (un-deleting a soft-deleted project) are explicitly out of scope and will be delivered in a dedicated phase.
- Project name uniqueness is enforced across active projects only; a deleted project's name can be reused.
- No pagination is required for the project list in this phase; a future phase will add pagination when the dataset grows.
- No role-based restrictions are enforced on who can create, update, or delete a project in this phase; any authenticated user may perform any project operation.
- `description` is free-form text with no length constraint imposed in this phase.
