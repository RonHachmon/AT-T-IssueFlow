# Research: Tickets — Create, Read, Update, Transition, Soft-Delete

## Decision 1: API Path Shape

**Decision**: Use flat `/tickets` paths with `projectId` as a required query parameter for the list endpoint. Individual ticket operations use `/tickets/{ticketId}`.

**Rationale**: The README Tickets APIs table is the canonical API contract per Constitution IV. It defines `GET /tickets?projectId=:projectId`, `GET /tickets/:ticketId`, `POST /tickets`, `PATCH /tickets/:ticketId`, and `DELETE /tickets/:ticketId`. These README templates must be implemented exactly. The spec's `/projects/{projectId}/tickets` nesting was a technology-agnostic placeholder; the README overrides it.

**Alternatives considered**: Nested resource paths (`/projects/{id}/tickets`) — rejected because the README canonical template uses flat paths; deviating from the README is a Constitution IV violation.

---

## Decision 2: Status Transition Mechanism

**Decision**: Status changes are handled by the existing `PATCH /tickets/{ticketId}` endpoint when the request body includes a `status` field. No separate transition endpoint is created.

**Rationale**: The README `PATCH /tickets/:ticketId` request body example explicitly includes `"status": "IN_PROGRESS"` — meaning the README defines the PATCH endpoint as the single surface for both field updates and status transitions. The service applies forward-only validation whenever a `status` field is present in the PATCH body.

**Alternatives considered**: Dedicated `POST /tickets/{id}/transitions` endpoint — rejected because the README does not define such an endpoint, and creating one without a README template would introduce an undocumented contract deviation.

---

## Decision 3: Optimistic Locking

**Decision**: Use JPA `@Version` annotation on the `Ticket` entity (backed by a `version BIGINT NOT NULL DEFAULT 0` column). Spring Data throws `ObjectOptimisticLockingFailureException` when a stale-version write is attempted. A new handler added to `GlobalExceptionHandler` maps this to 409 Conflict using the existing `ProblemDetailFactory.conflict()` method.

**Rationale**: JPA optimistic locking is a battle-tested, non-intrusive mechanism that requires no protocol changes (clients never need to pass a version token). It provides the exact "one wins, one gets a clear 409" guarantee the spec requires, and it slots naturally into the project's existing exception-handler pattern.

**Alternatives considered**: Client-side versioning (ETag / `If-Match` header) — provides the same guarantee but adds HTTP header complexity and a new error type; not warranted when JPA already handles it. Pessimistic locking — serializes all concurrent writes on the same ticket; unnecessary overhead for the expected access patterns.

---

## Decision 4: Frozen DONE Tickets

**Decision**: A new `InvalidStateTransitionException` is introduced in the `common/error` package. It carries a human-readable reason string and maps to 409 Conflict via a new handler in `GlobalExceptionHandler` using `ProblemDetailFactory.conflict()`. The same exception covers: (a) editing a DONE ticket, (b) backward transitions, and (c) skip-step transitions.

**Rationale**: Reusing `DuplicateResourceException` would be semantically misleading. The `conflict()` factory method already exists in `ProblemDetailFactory` and `CONFLICT` already exists in `ErrorType`, so the new exception needs no new infrastructure — only the exception class and one handler method.

**Alternatives considered**: Using a boolean flag on the exception to distinguish transition vs. frozen — rejected by Constitution I (no flag arguments); the exception message conveys the distinction to clients without a flag.

---

## Decision 5: dueDate and isOverdue

**Decision**: `dueDate` is stored as a `TIMESTAMPTZ` column (optional). `isOverdue` is a computed response field: `dueDate != null && dueDate.isBefore(Instant.now())`. The computation happens in `TicketMapper` using a default method that accesses the current instant.

**Rationale**: The README response body includes both fields. `isOverdue` is a derived view of `dueDate` — there is no benefit to persisting it (it would become stale immediately). Putting the computation in the mapper keeps the entity clean and the service unaware of this display concern.

**Alternatives considered**: Computing `isOverdue` in the service or controller — rejected because this is a mapping concern; putting it in the mapper matches the principle that entities should not carry display logic.

---

## Decision 6: Enum Persistence Strategy

**Decision**: `TicketStatus`, `TicketPriority`, and `TicketType` are Java enums annotated `@Enumerated(EnumType.STRING)` on the entity field. The database columns are `VARCHAR(20)` storing the enum name directly.

**Rationale**: `EnumType.STRING` makes the SQL table human-readable and survives enum reordering without breaking existing data. A PostgreSQL `ENUM` type would require a migration for every new value, adding friction for future evolution.

**Alternatives considered**: PostgreSQL `ENUM` type — more constrained at the DB level but requires a DDL migration for every value addition. `EnumType.ORDINAL` — compact but fragile (inserting a value in the middle breaks all existing rows).

---

## Decision 7: List Response Shape

**Decision**: `GET /tickets?projectId=:projectId` returns a plain JSON array (`List<TicketResponse>`), matching the README template exactly. No `PagedResponse` envelope is used.

**Rationale**: The README shows a plain array `[ { ... } ]` for the ticket list endpoint. The canonical README template takes precedence over the project's `PagedResponse` convention (Constitution IV explicitly states README templates override general conventions). Pagination can be added in a future phase.

**Alternatives considered**: Wrapping in `PagedResponse` — rejected because the README template shows a plain array; applying the envelope would deviate from the canonical contract.

---

## Decision 8: PATCH Status Restriction

**Decision**: The PATCH request DTO (`UpdateTicketRequest`) includes an optional `status` field. The service's `update` method applies two guards when `status` is non-null: (1) if the ticket is `DONE`, throw `InvalidStateTransitionException`; (2) if the requested status is not exactly the next step in the sequence (`ordinal + 1`), throw `InvalidStateTransitionException`.

**Rationale**: Encoding the forward-only constraint in the service keeps the guard close to the data and ensures it is enforced regardless of how the endpoint is called. Using `TicketStatus.ordinal()` to compare avoids magic strings.

**Alternatives considered**: Encoding allowed transitions in a map — more flexible for complex workflows but over-engineered for a four-state linear sequence. Validating in the controller — rejected; business rules belong in the service (Constitution I, single abstraction level).
