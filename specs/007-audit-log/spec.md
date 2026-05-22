# Feature Specification: Audit Log — Immutable Record of All Entity Changes

**Feature Branch**: `007-audit-log`
**Created**: 2026-05-22
**Status**: Draft
**Input**: User description: "Record everything that changes. Every time a user (or the system itself, in later phases) creates, updates, or deletes something, a permanent record is written: who did it, when, what kind of action, on what kind of thing, and which specific record. These logs can never be edited or deleted — only read. There's an endpoint to list logs, with the ability to filter by the kind of thing, a specific record, the kind of action, or who performed it."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Automatic Recording of Entity Changes (Priority: P1)

Whenever an authenticated user creates, updates, or deletes any audited entity (User, Project, Ticket, Comment), the system automatically writes a permanent audit entry capturing who performed the change, when it happened, what kind of action it was, what kind of entity was affected, and which specific record was affected. The user does not have to do anything additional — the recording is a side effect of the change itself.

**Why this priority**: Without the recording mechanism, there is nothing to read, filter, or protect. Every other story in this feature depends on entries existing. This is the foundational slice.

**Independent Test**: Perform a write on any audited entity (e.g., `POST /projects`, `PATCH /projects/{id}/tickets/{id}`, `DELETE /projects/{id}/tickets/{id}/comments/{id}`). Then issue `GET /audit-logs` as an administrator. The response includes a new entry whose `actorId` matches the calling user, `action` matches the operation kind, `entityType` matches the entity, `entityId` matches the affected record's ID, and `timestamp` is within seconds of the operation.

**Acceptance Scenarios**:

1. **Given** an authenticated user with `id: 7` and an existing project, **When** the user posts a new ticket via `POST /projects/1/tickets` and receives `201 Created` with the new ticket `id: 42`, **Then** a new audit entry exists with `actorId: 7`, `action: CREATE`, `entityType: TICKET`, `entityId: 42`, and a `timestamp` matching the operation time.
2. **Given** an authenticated user updates a ticket's status, **When** the operation succeeds, **Then** exactly one audit entry is written with `action: UPDATE` and `entityType: TICKET` referencing the updated ticket.
3. **Given** an authenticated user soft-deletes a project, **When** the operation succeeds, **Then** an audit entry is written with `action: DELETE` and `entityType: PROJECT` referencing the deleted project's ID (the entry persists even though the project is no longer active).
4. **Given** an authenticated user hard-deletes a comment, **When** the operation succeeds, **Then** an audit entry is written with `action: DELETE`, `entityType: COMMENT`, and `entityId` set to the deleted comment's former ID. The audit entry remains readable after the underlying comment row no longer exists.
5. **Given** an operation that fails after validation but before persistence (e.g., the request body is invalid or the resource is not found), **When** the response is `4xx`, **Then** no audit entry is written for that attempt.
6. **Given** an operation that begins persistence but fails midway and is rolled back (e.g., constraint violation, optimistic-lock conflict), **When** the transaction is rolled back, **Then** no audit entry is written — the audit record's existence is consistent with the change actually taking effect.

---

### User Story 2 - Read the Audit Log (Priority: P1)

An administrator retrieves a paginated list of audit entries through a single read endpoint. Entries are returned newest first so the most recent activity is immediately visible.

**Why this priority**: Recording without reading is wasted. The list endpoint is the primary user-facing surface and the only way the value of Story 1 becomes visible. It is co-equal P1 with recording because either alone delivers no usable value.

**Independent Test**: Once at least one audited operation has occurred (per Story 1), `GET /audit-logs` returns a paginated response whose `data` array contains the recorded entries, sorted by `timestamp` descending.

**Acceptance Scenarios**:

1. **Given** several audited operations have occurred, **When** an administrator calls `GET /audit-logs`, **Then** the response is `200 OK` with a paginated envelope (`data`, `page`, `pageSize`, `total`) containing those entries ordered by `timestamp` descending.
2. **Given** no audited operations have occurred yet, **When** `GET /audit-logs` is called, **Then** the response is `200 OK` with an empty `data` array and `total: 0`.
3. **Given** a non-administrator authenticated user, **When** they call `GET /audit-logs`, **Then** the response is `403 Forbidden`.
4. **Given** an unauthenticated request, **When** `GET /audit-logs` is called, **Then** the response is `401 Unauthorized`.
5. **Given** more entries exist than fit in a single page, **When** the caller requests page 2, **Then** the response contains the next slice of entries with consistent ordering and no duplicates or gaps relative to page 1.

---

### User Story 3 - Filter the Audit Log (Priority: P2)

An administrator narrows the audit log to a subset of interest by filtering on entity type, a specific record (entity ID), action type, or actor. Filters combine with AND semantics so multiple criteria can be applied together.

**Why this priority**: Without filters the list is still functional but quickly becomes unusable as the system accumulates entries. Filtering is essential to answer questions like "what did user 7 do this week" or "who deleted ticket 42", but the unfiltered list (Story 2) is independently valuable for small volumes.

**Independent Test**: With entries spanning multiple entity types, actions, and actors, calling `GET /audit-logs?entityType=TICKET&action=DELETE` returns only the subset matching both criteria, and the total count reflects the filtered result.

**Acceptance Scenarios**:

1. **Given** entries for multiple entity types exist, **When** `GET /audit-logs?entityType=TICKET` is called, **Then** the response contains only entries with `entityType: TICKET`.
2. **Given** entries for ticket 5 and other tickets exist, **When** `GET /audit-logs?entityType=TICKET&entityId=5` is called, **Then** the response contains only entries for ticket 5.
3. **Given** entries with mixed actions exist, **When** `GET /audit-logs?action=DELETE` is called, **Then** the response contains only entries with `action: DELETE`.
4. **Given** user 7 and user 8 have both performed audited operations, **When** `GET /audit-logs?actorId=7` is called, **Then** the response contains only entries with `actorId: 7`.
5. **Given** all four filters are supplied together, **When** `GET /audit-logs?entityType=COMMENT&entityId=12&action=UPDATE&actorId=3` is called, **Then** the response contains only entries matching every criterion (AND semantics).
6. **Given** a filter value is supplied that matches no entries, **When** the request is made, **Then** the response is `200 OK` with an empty `data` array (not `404`).
7. **Given** an unknown `entityType` or `action` value is supplied (e.g., `entityType=BANANA`), **When** the request is made, **Then** the response is `400 Bad Request`.
8. **Given** `entityId` is supplied without `entityType`, **When** the request is made, **Then** the response is `400 Bad Request` (an entity ID is meaningless without an entity type because IDs are not globally unique across types).

---

### User Story 4 - Immutability Guarantee (Priority: P3)

The audit log cannot be modified or deleted through the API. There is no endpoint to edit an audit entry, no endpoint to delete one, no bulk-purge mechanism — only the read endpoint exists.

**Why this priority**: Immutability is the property that makes the audit log trustworthy. It is a P3 because in practice it is enforced by *absence* of write endpoints rather than active behaviour, so its verification is largely negative testing. The recording and reading slices must exist first to make this meaningful.

**Independent Test**: Attempt `PUT`, `PATCH`, or `DELETE` against `/audit-logs` or `/audit-logs/{id}`. Every such request returns `405 Method Not Allowed` or `404 Not Found` because no such routes are defined. The OpenAPI surface for the resource exposes only `GET`.

**Acceptance Scenarios**:

1. **Given** an existing audit entry with id 42, **When** any authenticated user (including an administrator) issues `DELETE /audit-logs/42`, **Then** the response is `405 Method Not Allowed` or `404 Not Found`.
2. **Given** an existing audit entry with id 42, **When** any authenticated user issues `PATCH /audit-logs/42` or `PUT /audit-logs/42`, **Then** the response is `405 Method Not Allowed` or `404 Not Found`.
3. **Given** an existing audit entry, **When** an entity it references is later deleted (e.g., the comment is hard-deleted or the user is removed), **Then** the audit entry remains readable with its original `actorId`, `entityType`, and `entityId` intact.
4. **Given** the source row referenced by `entityId` has been deleted, **When** an administrator reads the audit log, **Then** the entry still appears with its original metadata — the audit log never silently drops entries because their target no longer exists.

---

### Edge Cases

- What happens when a write succeeds but the audit-entry write fails? → The whole operation is rolled back. The change and the audit entry succeed or fail together; the audit log never lags or drifts from the underlying state.
- What happens when a user is hard-deleted and they have prior audit entries? → The entries persist with the original `actorId`. The audit log does not cascade and does not null out historical actors.
- What happens when an entity is referenced by audit entries and then re-created with the same numeric ID? → The audit entries point at the new row by coincidence of ID. This is acceptable because `actorId`/`entityType`/`entityId`/`timestamp` together still describe the original event, and IDs in this system are not reused by the database in normal operation (sequence-based).
- What happens when an administrator passes `actorId=999` for a user that does not exist? → `200 OK` with an empty `data` array. The filter applies even if the user is gone.
- What happens when an administrator pages past the end of the available entries? → `200 OK` with an empty `data` array and the unchanged `total`.
- What happens when the same operation triggers writes to multiple entities (e.g., a future cascade operation)? → One audit entry per entity changed. Each entity's change is recorded separately even if they share a single API call.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST write exactly one audit entry per successful create, update, or delete of any audited entity. The audited entities in this phase are `USER`, `PROJECT`, `TICKET`, and `COMMENT`.
- **FR-002**: Each audit entry MUST capture: a generated `id`, a `timestamp` set at the time of the operation, the `actorId` of the user who performed the operation (taken from the authenticated principal), the `action` (`CREATE`, `UPDATE`, or `DELETE`), the `entityType` (one of `USER`, `PROJECT`, `TICKET`, `COMMENT`), and the `entityId` of the affected record.
- **FR-003**: System MUST write the audit entry within the same transaction as the underlying change, so that a rollback of the change also rolls back the audit entry, and a committed change is always accompanied by its audit entry.
- **FR-004**: System MUST NOT write an audit entry for read operations, login attempts, or any request that fails before successful persistence.
- **FR-005**: System MUST expose `GET /audit-logs` returning a paginated list of audit entries ordered by `timestamp` descending (most recent first) using the project's canonical pagination envelope.
- **FR-006**: System MUST restrict the audit-log read endpoint to users with the `ADMIN` authority. Non-administrators receive `403 Forbidden`; unauthenticated requests receive `401 Unauthorized`.
- **FR-007**: System MUST support filtering the audit log by `entityType`, `entityId`, `action`, and `actorId` as query parameters; filters combine with AND semantics.
- **FR-008**: System MUST reject `entityId` supplied without `entityType` with `400 Bad Request`, because entity IDs are not globally unique across entity types.
- **FR-009**: System MUST reject unknown values for `entityType` or `action` filter parameters with `400 Bad Request`.
- **FR-010**: System MUST NOT expose any endpoint that creates, updates, or deletes an audit entry. The audit log is read-only through the API.
- **FR-011**: Audit entries MUST remain readable after the entity they reference is deleted (soft- or hard-deleted) or the user named in `actorId` is deleted. Entries are never cascade-deleted.
- **FR-012**: System MUST NOT write an audit entry for the creation of an audit entry. Audit entries are not themselves audited.
- **FR-013**: System MUST preserve the original `actorId`, `entityType`, `entityId`, `action`, and `timestamp` of every entry. No mechanism in the system updates these fields after the entry is written.

### Key Entities

- **AuditEntry**: A permanent record of a single state-changing operation. Key attributes: `id` (generated), `timestamp` (set at write time, never modified), `actorId` (reference to the User who performed the operation; nullable to allow system-actor entries in later phases), `action` (enum: `CREATE`, `UPDATE`, `DELETE`), `entityType` (enum: `USER`, `PROJECT`, `TICKET`, `COMMENT`), `entityId` (numeric identifier of the affected record; not a foreign key, since referential integrity must not block writes when the target is later deleted).
- **User** (existing): Referenced as the actor of an audit entry. No changes to the User entity in this phase. Audit entries persist even after the referenced user is deleted.
- **Project**, **Ticket**, **Comment** (existing): Audited entities. Their write operations trigger audit entries as a side effect; their own schemas do not change in this phase.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of successful create, update, and delete operations on audited entities produce exactly one audit entry with all required fields populated — zero missed events under normal operation.
- **SC-002**: 0% of failed or rolled-back operations leave a stranded audit entry — audit entries and entity changes always agree.
- **SC-003**: The audit log read endpoint returns the first page of results within 300 ms for an entry corpus up to 100,000 entries under normal load.
- **SC-004**: Filtered queries return only entries matching every supplied filter — independent inspection of returned entries confirms 100% precision for any combination of the four filters.
- **SC-005**: No API path exists through which an audit entry can be modified or deleted — verified by route inventory and by negative tests against `PUT`, `PATCH`, and `DELETE` on the `/audit-logs` resource.
- **SC-006**: A non-administrator authenticated user receives `403 Forbidden` on 100% of attempts to read the audit log.

## Assumptions

- Auditing is restricted to the four existing aggregate roots (`USER`, `PROJECT`, `TICKET`, `COMMENT`). Authentication events (login, token issuance) are not audited in this phase, since the user description scopes auditing to "creates, updates, or deletes something" — i.e., entity state changes.
- The read endpoint is administrator-only by default, following the industry-standard treatment of audit data as sensitive. This may be relaxed in a future phase (e.g., users reading their own activity) but is not in scope here.
- Audit entries capture metadata only (who/when/what/which). The before/after values of changed fields are not captured in this phase — the user description explicitly enumerates the five fields to record, and a structured diff is not among them.
- The actor for every audited operation in this phase is the authenticated user from the JWT. The "system itself" actor mentioned in the description is intentionally deferred to a later phase, modelled by leaving `actorId` nullable in the schema from day one.
- Audit entries are written in the same database transaction as the entity change, using the existing relational store. There is no separate audit service or message bus in scope.
- Pagination follows the same canonical envelope (`data`, `page`, `pageSize`, `total`) used by all other paginated endpoints in the project. Page-size defaults and bounds match existing endpoints.
- Date-range filtering (`from`/`to` timestamps) is not in scope for this phase — the user description enumerates four filter dimensions and does not mention time-range filtering. It may be added later without breaking the contract.
- `entityId` is stored as a plain numeric reference, not as a foreign key, so deletion of the underlying entity does not require any audit-side cascade or cleanup. Referential integrity between audit entries and their targets is intentionally not enforced at the database layer.
- The audit log endpoint is exempt from being audited itself (it is a read), and audit-entry creation is exempt from being audited (to avoid infinite recursion).
