# Feature Specification: Ticket Comments — Post, List, Edit, Delete

**Feature Branch**: `006-ticket-comments`
**Created**: 2026-05-22
**Status**: Draft
**Input**: User description: "Let users discuss tickets. Anyone can post a comment on a ticket, see all comments on a ticket, edit a comment, or delete one. Like tickets, comments have the same protection against two people editing the same comment at the same time."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Post a Comment (Priority: P1)

An authenticated user posts a comment on a specific ticket by providing the comment text. The system records the author automatically from the bearer token. The new comment is immediately visible in the ticket's comment list.

**Why this priority**: Posting a comment is the foundational operation. Without it, listing, editing, and deleting are meaningless. It establishes the Comment entity and the association with a ticket.

**Independent Test**: `POST /projects/{projectId}/tickets/{ticketId}/comments` with a valid body → `201 Created` with a comment object containing a generated ID, the author's ID, and timestamps. Then `GET /projects/{projectId}/tickets/{ticketId}/comments` returns it in the list.

**Acceptance Scenarios**:

1. **Given** an active ticket with ID 3 in project 1, **When** `POST /projects/1/tickets/3/comments` is called with `{ "body": "We should prioritise this fix." }`, **Then** the response is `201 Created` with a comment object containing a generated ID, `authorId` set to the calling user's ID, `ticketId: 3`, `body: "We should prioritise this fix."`, and `createdAt`/`updatedAt` timestamps.
2. **Given** a valid ticket, **When** `POST` is called with an empty `body` or a body containing only whitespace, **Then** the response is `400 Bad Request` with a validation error.
3. **Given** a valid ticket, **When** `POST` is called without a `body` field at all, **Then** the response is `400 Bad Request`.
4. **Given** no ticket with ID 999 exists (or it is soft-deleted), **When** `POST /projects/{projectId}/tickets/999/comments` is called, **Then** the response is `404 Not Found`.
5. **Given** a valid ticket in project 99 where project 99 does not exist, **When** `POST /projects/99/tickets/{id}/comments` is called, **Then** the response is `404 Not Found`.

---

### User Story 2 - List Comments on a Ticket (Priority: P1)

An authenticated user retrieves all comments on a specific ticket. Comments are returned in chronological order (oldest first) in a paginated list.

**Why this priority**: Reading comments is the most frequent operation and confirms the entity is correctly persisted. It is required for any meaningful display of discussion.

**Independent Test**: With one or more comments posted (Story 1), `GET /projects/{projectId}/tickets/{ticketId}/comments` returns them in a paginated envelope ordered by `createdAt` ascending. A ticket with no comments returns an empty list, not `404`.

**Acceptance Scenarios**:

1. **Given** a ticket has three comments, **When** `GET /projects/{projectId}/tickets/{ticketId}/comments` is called, **Then** the response is `200 OK` with a paginated list of those three comments sorted by `createdAt` ascending.
2. **Given** a ticket has no comments, **When** `GET /projects/{projectId}/tickets/{ticketId}/comments` is called, **Then** the response is `200 OK` with an empty `data` array.
3. **Given** no ticket with ID 999 exists (or it is soft-deleted), **When** `GET /projects/{projectId}/tickets/999/comments` is called, **Then** the response is `404 Not Found`.
4. **Given** a deleted comment existed on a ticket, **When** `GET` is called on that ticket, **Then** the deleted comment does not appear in the list.

---

### User Story 3 - Edit a Comment (Priority: P2)

An authenticated user updates the body text of an existing comment. The system uses optimistic locking: the caller must supply the current version of the comment, and if the comment has been modified by someone else since it was last read, the update is rejected with a conflict error.

**Why this priority**: Editing enables correction of mistakes. The version-based conflict guard is explicitly required by the feature description and mirrors the ticket concurrency behaviour.

**Independent Test**: `PATCH /projects/{projectId}/tickets/{ticketId}/comments/{commentId}` with `{ "body": "Updated text", "version": N }` → `200 OK` with updated comment and incremented version. Same call with a stale version → `409 Conflict`.

**Acceptance Scenarios**:

1. **Given** a comment at version 0, **When** `PATCH` is called with `{ "body": "Revised text", "version": 0 }`, **Then** the response is `200 OK` with the updated `body`, a refreshed `updatedAt`, and `version: 1`.
2. **Given** a comment at version 1 (another user has already edited it), **When** `PATCH` is called with `{ "body": "My text", "version": 0 }` (stale version), **Then** the response is `409 Conflict` indicating a concurrent modification was detected.
3. **Given** two concurrent edit requests for the same comment both supplying version 0, **When** both are processed, **Then** exactly one returns `200 OK` and the other returns `409 Conflict` — no silent data loss occurs.
4. **Given** a valid comment, **When** `PATCH` is called with an empty or whitespace-only `body`, **Then** the response is `400 Bad Request`.
5. **Given** no comment with ID 999 exists, **When** `PATCH` is called, **Then** the response is `404 Not Found`.
6. **Given** a valid comment, **When** `PATCH` is called without a `version` field, **Then** the response is `400 Bad Request`.

---

### User Story 4 - Delete a Comment (Priority: P3)

An authenticated user permanently removes a comment from a ticket. The comment disappears from the ticket's comment list immediately.

**Why this priority**: Deletion rounds out the full CRUD surface. It depends on Stories 1 and 2 being stable and is the lowest-risk operation.

**Independent Test**: `DELETE /projects/{projectId}/tickets/{ticketId}/comments/{commentId}` → `204 No Content` → `GET /projects/{projectId}/tickets/{ticketId}/comments` no longer includes the deleted comment.

**Acceptance Scenarios**:

1. **Given** a comment with ID 5 on ticket 3 in project 1, **When** `DELETE /projects/1/tickets/3/comments/5` is called, **Then** the response is `204 No Content`.
2. **Given** the deleted comment from scenario 1, **When** `GET /projects/1/tickets/3/comments` is called, **Then** the comment is absent from the list.
3. **Given** no comment with ID 999 exists, **When** `DELETE` is called, **Then** the response is `404 Not Found`.
4. **Given** an already-deleted comment, **When** `DELETE` is called again, **Then** the response is `404 Not Found` (idempotent at the API level).

---

### Edge Cases

- What happens when `body` exceeds a reasonable maximum length (e.g., 2000 characters)? → `400 Bad Request`.
- What happens when a comment is posted on a `DONE` ticket? → Allowed; comments are not restricted by ticket status.
- What happens when a comment is posted on a ticket whose parent project is soft-deleted? → `404 Not Found` (the ticket itself is unreachable).
- What happens when `GET /projects/{projectId}/tickets/{ticketId}/comments` is called for a non-existent project? → `404 Not Found`.
- What happens when two users concurrently edit the same comment with the correct version? → One succeeds; the other receives `409 Conflict`. The losing user must re-fetch and retry.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow any authenticated user to post a comment on an existing, non-deleted ticket by supplying a non-blank `body`.
- **FR-002**: System MUST automatically record the authenticated user as the comment's author; the caller cannot supply a different `authorId`.
- **FR-003**: System MUST reject comment creation if the referenced ticket does not exist or is soft-deleted.
- **FR-004**: System MUST reject comment creation if `body` is blank, whitespace-only, or absent.
- **FR-005**: System MUST reject comment creation if `body` exceeds 2000 characters.
- **FR-006**: System MUST return all non-deleted comments for a given ticket in a paginated list ordered by `createdAt` ascending.
- **FR-007**: System MUST return `404 Not Found` when listing, editing, or deleting comments on a ticket that does not exist or is soft-deleted.
- **FR-008**: System MUST allow any authenticated user to update the `body` of any comment via a `PATCH` request that includes the current `version`.
- **FR-009**: System MUST implement optimistic locking for comment edits: if the supplied `version` does not match the stored version, the update MUST be rejected with `409 Conflict`.
- **FR-010**: System MUST reject a `PATCH` request missing the `version` field with `400 Bad Request`.
- **FR-011**: System MUST reject a `PATCH` request with an empty or whitespace-only `body` with `400 Bad Request`.
- **FR-012**: System MUST allow any authenticated user to permanently delete any comment; deleted comments are removed from all subsequent list and fetch responses.
- **FR-013**: System MUST record `createdAt` on creation and `updatedAt` on every edit; `version` is incremented on each successful edit.
- **FR-014**: All comment endpoints MUST require a valid bearer token (per existing JWT auth).

### Key Entities

- **Comment**: A discussion entry attached to a ticket. Key attributes: `id` (generated), `body` (required, max 2000 characters), `ticketId` (required reference to a Ticket), `authorId` (required reference to the posting User, set automatically), `createdAt`, `updatedAt`, `version` (integer, for optimistic locking, starts at 0).
- **Ticket** (existing): The parent work item. No changes to the Ticket entity in this phase.
- **User** (existing): Referenced as the comment author. No changes to the User entity in this phase.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Any authenticated user can post, list, edit, and delete a comment in under 30 seconds of total interaction across all four operations.
- **SC-002**: Comment list and single-comment responses are returned within 200 ms under normal load (single-node, no concurrent stress).
- **SC-003**: Concurrent edit conflicts are surfaced as explicit `409 Conflict` responses 100% of the time — zero silent overwrites.
- **SC-004**: 100% of requests with missing or blank `body`, or missing `version` on edit, return structured `400 Bad Request` errors sufficient for a UI to display without additional lookups.
- **SC-005**: Deleted comments never appear in list responses — zero leakage of deleted data.

## Assumptions

- Any authenticated user may edit or delete any comment regardless of authorship; no ownership restriction is enforced in this phase.
- Comments are hard-deleted (physically removed); soft-delete behaviour is not required for comments in this phase.
- Commenting on a `DONE` (frozen) ticket is permitted; the frozen status applies only to ticket field updates and status transitions, not to discussion.
- `body` maximum length is 2000 characters, consistent with common issue-tracker defaults; this may be relaxed in a future phase.
- The `authorId` of a comment is immutable after creation; it cannot be changed by any subsequent edit.
- No rich-text, Markdown rendering, or attachment support is in scope for this phase; `body` is plain text.
- Comment pagination follows the same canonical shape used by all other paginated endpoints in the project.
- Filtering or sorting comments (e.g., by author, date range) is out of scope for this phase.
