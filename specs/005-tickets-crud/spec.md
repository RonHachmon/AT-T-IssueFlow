# Feature Specification: Tickets — Create, Read, Update, Transition, Soft-Delete

**Feature Branch**: `005-tickets-crud`
**Created**: 2026-05-21
**Status**: Draft
**Input**: User description: "Add the actual work items. Each ticket belongs to one project and has a title, description, status (TODO → IN_PROGRESS → IN_REVIEW → DONE), priority (LOW/MEDIUM/HIGH/CRITICAL), type (BUG/FEATURE/TECHNICAL), and optionally an assigned developer. The lifecycle is strict: tickets can only move forward through statuses, never backward. Once DONE, a ticket is frozen — no further edits. When two people try to update the same ticket simultaneously, one of them gets a clear conflict error rather than silently overwriting the other's changes. Tickets are soft-deleted just like projects."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create a Ticket (Priority: P1)

A user creates a new ticket inside an existing project by providing a title, optional description, priority, type, and optionally assigning it to a developer. The ticket starts in the `TODO` status automatically. The newly created ticket is immediately visible in the project's ticket list.

**Why this priority**: Tickets are the core work items of the system. Creating a ticket is the foundational operation that all other ticket operations depend on. Without this, no workflow, status progression, or assignment is possible.

**Independent Test**: `POST /projects/{projectId}/tickets` with valid fields → `201 Created` with a ticket object whose `status` is `TODO`. Then `GET /projects/{projectId}/tickets/{id}` returns the same ticket.

**Acceptance Scenarios**:

1. **Given** an active project with ID 2 exists, **When** `POST /projects/2/tickets` is called with `{ "title": "Fix login bug", "description": "Users cannot log in with SSO", "priority": "HIGH", "type": "BUG" }`, **Then** the response is `201 Created` with a ticket object containing a generated ID, `status: "TODO"`, the provided fields, and a `createdAt` timestamp.
2. **Given** a valid project, **When** `POST /projects/{id}/tickets` is called with `title` omitted, **Then** the response is `400 Bad Request` with a validation error for the missing field.
3. **Given** a valid project, **When** `POST /projects/{id}/tickets` is called with an invalid `priority` value (e.g., `"ULTRA"`), **Then** the response is `400 Bad Request`.
4. **Given** a valid project, **When** `POST /projects/{id}/tickets` is called with an `assigneeId` that does not reference an existing user, **Then** the response is `404 Not Found`.
5. **Given** no project with ID 999 exists (or it is soft-deleted), **When** `POST /projects/999/tickets` is called, **Then** the response is `404 Not Found`.

---

### User Story 2 - Fetch and List Tickets (Priority: P1)

Users can retrieve a single ticket by its ID within a project, or list all active (non-deleted) tickets belonging to a project.

**Why this priority**: Reading ticket data is the most frequent operation. It confirms the data model and soft-delete behaviour, and it is required before any UI or integration can display work items.

**Independent Test**: With at least one ticket created (Story 1), `GET /projects/{projectId}/tickets` returns it in a paginated list. `GET /projects/{projectId}/tickets/{id}` returns it individually. A soft-deleted ticket does not appear in the list.

**Acceptance Scenarios**:

1. **Given** a project has three active tickets, **When** `GET /projects/{projectId}/tickets` is called, **Then** the response is `200 OK` with a paginated list containing those three tickets.
2. **Given** a ticket with ID 7 exists in project 2, **When** `GET /projects/2/tickets/7` is called, **Then** the response is `200 OK` with that ticket's data.
3. **Given** no ticket with ID 999 exists in the project (or it is soft-deleted), **When** `GET /projects/{projectId}/tickets/999` is called, **Then** the response is `404 Not Found`.
4. **Given** a ticket has been soft-deleted, **When** `GET /projects/{projectId}/tickets` is called, **Then** the deleted ticket does not appear in the list.
5. **Given** a project has no tickets, **When** `GET /projects/{projectId}/tickets` is called, **Then** the response is `200 OK` with an empty list, not `404`.

---

### User Story 3 - Update Ticket Fields (Priority: P2)

Users can update a ticket's title, description, priority, type, and assigned developer — but only while the ticket is not `DONE`. Attempting to update a `DONE` ticket is rejected. Status changes are handled by a dedicated transition operation (Story 4), not by this update endpoint.

**Why this priority**: Field updates are routine operations needed for day-to-day task management. They depend on ticket creation (Story 1) being stable, but are not blockers for reading data.

**Independent Test**: `PATCH /projects/{projectId}/tickets/{id}` with a new `priority` → `200 OK` → `GET` reflects the new value. Attempting the same on a `DONE` ticket returns `409 Conflict`.

**Acceptance Scenarios**:

1. **Given** an active ticket in `TODO` status, **When** `PATCH /projects/{projectId}/tickets/{id}` is called with `{ "priority": "CRITICAL" }`, **Then** the response is `200 OK` with the updated ticket and a refreshed `updatedAt`.
2. **Given** an active ticket, **When** `PATCH` is called with `{ "assigneeId": null }`, **Then** the assignee is cleared and the response is `200 OK`.
3. **Given** an active ticket, **When** `PATCH` is called with an `assigneeId` that does not reference an existing user, **Then** the response is `404 Not Found`.
4. **Given** a ticket in `DONE` status, **When** `PATCH /projects/{projectId}/tickets/{id}` is called with any field update, **Then** the response is `409 Conflict` indicating the ticket is frozen.
5. **Given** a ticket does not exist or is soft-deleted, **When** `PATCH` is called, **Then** the response is `404 Not Found`.
6. **Given** a valid ticket and a request body `{}` (no fields), **When** `PATCH` is called, **Then** the response is `400 Bad Request` — at least one field must be provided.
7. **Given** `PATCH` body contains a `status` field, **When** processed, **Then** the `status` field is ignored — status transitions are performed via the dedicated transition endpoint.

---

### User Story 4 - Advance Ticket Status (Priority: P2)

Users move a ticket forward through its lifecycle: `TODO → IN_PROGRESS → IN_REVIEW → DONE`. Backward transitions are rejected. Transitioning a `DONE` ticket to any status is rejected. When two users attempt to update the same ticket concurrently, one succeeds and the other receives a clear conflict error.

**Why this priority**: Status transitions are the heartbeat of an issue tracker. They encode the forward-only workflow constraint and the concurrency protection that are central to the feature's value.

**Independent Test**: `POST /projects/{projectId}/tickets/{id}/transitions` with `{ "status": "IN_PROGRESS" }` on a `TODO` ticket → `200 OK` with updated ticket. Same call on a `DONE` ticket → `409`. A backward transition call → `409`.

**Acceptance Scenarios**:

1. **Given** a ticket in `TODO` status, **When** a transition to `IN_PROGRESS` is requested, **Then** the response is `200 OK` with `status: "IN_PROGRESS"`.
2. **Given** a ticket in `IN_PROGRESS` status, **When** a transition to `IN_REVIEW` is requested, **Then** the response is `200 OK` with `status: "IN_REVIEW"`.
3. **Given** a ticket in `IN_REVIEW` status, **When** a transition to `DONE` is requested, **Then** the response is `200 OK` with `status: "DONE"`.
4. **Given** a ticket in `IN_PROGRESS` status, **When** a transition back to `TODO` is requested, **Then** the response is `409 Conflict` with a message indicating backward transitions are not allowed.
5. **Given** a ticket in `DONE` status, **When** any transition is requested, **Then** the response is `409 Conflict` indicating the ticket is frozen.
6. **Given** two concurrent requests attempt to transition the same ticket simultaneously, **When** both are processed, **Then** one returns `200 OK` and the other returns `409 Conflict` with a message indicating a concurrent modification was detected — no silent data loss occurs.
7. **Given** a ticket that does not exist or is soft-deleted, **When** a transition is requested, **Then** the response is `404 Not Found`.

---

### User Story 5 - Soft-Delete a Ticket (Priority: P3)

Deleting a ticket marks it as deleted without physically removing its data. It disappears from normal listings and single-fetch operations.

**Why this priority**: Consistent with project soft-delete semantics. Depends on Stories 1 and 2 being stable.

**Independent Test**: Create a ticket, `DELETE /projects/{projectId}/tickets/{id}` → `204 No Content` → `GET /projects/{projectId}/tickets` no longer includes it → `GET /projects/{projectId}/tickets/{id}` returns `404`.

**Acceptance Scenarios**:

1. **Given** an active ticket with ID 5 in project 2, **When** `DELETE /projects/2/tickets/5` is called, **Then** the response is `204 No Content`.
2. **Given** the deleted ticket from scenario 1, **When** `GET /projects/2/tickets` is called, **Then** the ticket is absent from the list.
3. **Given** the deleted ticket from scenario 1, **When** `GET /projects/2/tickets/5` is called, **Then** the response is `404 Not Found`.
4. **Given** a ticket that does not exist, **When** `DELETE` is called, **Then** the response is `404 Not Found`.
5. **Given** an already soft-deleted ticket, **When** `DELETE` is called again, **Then** the response is `404 Not Found` (idempotent at the API level).

---

### Edge Cases

- What happens when `title` is an empty string or only whitespace? → `400 Bad Request` with a validation error.
- What happens when `title` exceeds a reasonable length (e.g., 255 characters)? → `400 Bad Request`.
- What happens when a transition skips a step (e.g., `TODO → DONE`)? → `409 Conflict`; only the immediate next status is valid.
- What happens when `GET /projects/{projectId}/tickets` is called for a soft-deleted project? → `404 Not Found`.
- What happens when a `DONE` ticket is soft-deleted? → Allowed; soft-delete is independent of status.
- What happens when two users concurrently update different fields of the same ticket? → One succeeds; the other receives `409 Conflict` with a message indicating the version has changed. The losing user must re-fetch and retry.
- What happens when `assigneeId` references a valid user who has no special role? → Assignment succeeds; no role restriction is enforced in this phase.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow creating a ticket within an existing active project, with a required `title` (non-blank, max 255 characters), optional `description`, required `priority` (one of LOW/MEDIUM/HIGH/CRITICAL), required `type` (one of BUG/FEATURE/TECHNICAL), and optional `assigneeId` referencing an existing user.
- **FR-002**: System MUST default the ticket `status` to `TODO` at creation; callers cannot set the initial status.
- **FR-003**: System MUST reject ticket creation if the parent project does not exist or is soft-deleted.
- **FR-004**: System MUST reject ticket creation if `assigneeId` is provided but does not reference an existing user.
- **FR-005**: System MUST return a single ticket by its ID within a project, excluding soft-deleted tickets.
- **FR-006**: System MUST return all active (non-deleted) tickets for a given project in a paginated list; soft-deleted tickets are excluded.
- **FR-007**: System MUST allow updating `title`, `description`, `priority`, `type`, and `assigneeId` on a ticket that is not in `DONE` status.
- **FR-008**: System MUST reject any field update (PATCH) on a ticket that is in `DONE` status with a `409 Conflict`.
- **FR-009**: System MUST provide a dedicated status-transition operation that moves a ticket to the next status in the sequence `TODO → IN_PROGRESS → IN_REVIEW → DONE`.
- **FR-010**: System MUST reject any backward status transition with a `409 Conflict`.
- **FR-011**: System MUST reject a status transition that skips a step (e.g., `TODO → DONE`) with a `409 Conflict`.
- **FR-012**: System MUST reject any status transition on a `DONE` ticket with a `409 Conflict`.
- **FR-013**: System MUST implement optimistic locking for concurrent modifications: when two requests attempt to update the same ticket simultaneously, exactly one MUST succeed and the other MUST receive a `409 Conflict` with a message indicating a concurrent modification was detected.
- **FR-014**: System MUST soft-delete a ticket by marking it as deleted rather than removing its record.
- **FR-015**: All ticket endpoints MUST require a valid bearer token (per existing JWT auth).
- **FR-016**: System MUST record `createdAt` on creation and `updatedAt` on every modification (field update or status transition).

### Key Entities

- **Ticket**: A unit of work belonging to a project. Key attributes: `id` (generated), `title` (required), `description` (optional), `status` (TODO/IN_PROGRESS/IN_REVIEW/DONE), `priority` (LOW/MEDIUM/HIGH/CRITICAL), `type` (BUG/FEATURE/TECHNICAL), `assigneeId` (optional reference to a User), `projectId` (required reference to a Project), `createdAt`, `updatedAt`, `deletedAt` (null = active), `version` (for optimistic locking).
- **Project** (existing): The parent container. Tickets reference a Project by ID. No changes to the Project entity in this phase.
- **User** (existing): Referenced as the optional ticket assignee. No changes to the User entity in this phase.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can create, retrieve, update, transition through all statuses, and delete a ticket in under 60 seconds of total interaction across those operations.
- **SC-002**: All ticket endpoints respond within 200 ms under normal load (single-node, no concurrent stress).
- **SC-003**: 100% of soft-deleted tickets are absent from list and single-fetch responses — zero leakage of deleted data.
- **SC-004**: 100% of backward and skip-step status transitions are rejected — no ticket can ever have its status regressed or jump out of sequence.
- **SC-005**: Concurrent modification conflicts are surfaced as explicit `409 Conflict` responses 100% of the time — zero silent overwrites.
- **SC-006**: All validation errors return structured, human-readable messages sufficient for a UI to display without additional lookups.

## Assumptions

- The caller is always an authenticated user with a valid bearer token; no anonymous ticket access is permitted.
- `assigneeId` must reference an existing user but no role check is applied — any user may be assigned to any ticket in this phase.
- The only supported status sequence is `TODO → IN_PROGRESS → IN_REVIEW → DONE`; non-linear workflows (e.g., parallel states, re-opening) are out of scope.
- Ticket name (title) uniqueness within a project is **not** enforced; duplicate titles are allowed.
- Restore endpoints (un-deleting a soft-deleted ticket) are explicitly out of scope and will be delivered in a dedicated phase.
- No role-based access control is applied to ticket operations in this phase; any authenticated user may create, update, transition, or delete any ticket.
- `description` is free-form text with no length constraint imposed in this phase.
- Filtering and sorting the ticket list (e.g., by status, priority, assignee) are out of scope for this phase; a future phase will add query parameters.
- The optimistic locking mechanism uses a version counter exposed in the API response; clients must echo the current version when submitting updates to detect conflicts.
