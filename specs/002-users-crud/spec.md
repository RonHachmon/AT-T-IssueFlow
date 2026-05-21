# Feature Specification: Users CRUD

**Feature Branch**: `002-users-crud`
**Created**: 2026-05-21
**Status**: Draft
**Input**: User description: "Add the concept of a person to the system. You can create users, fetch them, list them all, update their name or role, and delete them. Each user has a username, email, full name, and a role that is either ADMIN or DEVELOPER. No login yet — this phase just establishes who exists in the system."

## User Scenarios & Testing *(mandatory)*

> **Actors**. There is no end-user authentication yet (login arrives in a
> later feature). The "user" of this API is whoever holds the running
> server — an administrator setting up the system, a CI integration
> seeding test fixtures, or a developer poking the endpoints with a
> Postman collection. The records being managed represent the *people*
> the system will eventually track for tickets, comments, and audit
> entries.

### User Story 1 — Create a user (Priority: P1)

An administrator can register a new person in the system by providing
their username, email, full name, and role. Once accepted, the new user
has an identifier the system can reference everywhere a person is needed.

**Why this priority**: There is no system to talk about until at least
one person can be added. Every subsequent feature (tickets, comments,
assignments) keys off user IDs.

**Independent Test**: Submit a complete, valid user record to the create
endpoint. The response carries the persisted record including the new
identifier, and the user immediately appears in subsequent reads.

**Acceptance Scenarios**:

1. **Given** the system is running and no user with the username
   `alice` exists, **When** an administrator submits a user with
   username `alice`, email `alice@example.com`, full name `Alice Doe`,
   and role `DEVELOPER`, **Then** the response indicates creation
   succeeded, carries a server-assigned identifier, and the returned
   record echoes the submitted values.
2. **Given** a user with username `alice` already exists, **When** an
   administrator submits another user with the same username, **Then**
   the request is rejected with a conflict response that names the
   duplicate field.
3. **Given** the system is running, **When** an administrator submits a
   user with email `not-an-email`, **Then** the request is rejected
   with a validation response that names the invalid field and reason.

---

### User Story 2 — Fetch a user by id (Priority: P1)

Any caller can retrieve the full record for a single user by their
identifier. This is the read primitive every other feature builds on
when it says "the assignee" or "the author."

**Why this priority**: Without a single-user read, the only way to find
a record is the list endpoint, which is wasteful and brittle.

**Independent Test**: Create a user, then issue a fetch-by-id call with
that identifier. The response is the same record. Issuing the same call
with an unknown identifier returns a not-found response.

**Acceptance Scenarios**:

1. **Given** a user has been created with identifier `id`, **When** a
   caller fetches that identifier, **Then** the response carries the
   full user record.
2. **Given** no user exists with identifier `999999`, **When** a caller
   fetches that identifier, **Then** the response indicates the resource
   was not found.

---

### User Story 3 — List all users (Priority: P1)

A caller can retrieve the catalogue of all users. Because the list will
grow over time, results MUST be paginated — callers receive one page at
a time with an explicit total count.

**Why this priority**: Operators need a way to see who exists in the
system. Even with very few records, this is the canonical "show me
everyone" call that every admin UI and audit script will use.

**Independent Test**: Create three users, list them. All three appear in
the response with the documented pagination shape. Asking for a page
beyond the data returns an empty page, not an error.

**Acceptance Scenarios**:

1. **Given** zero users exist, **When** a caller lists users, **Then**
   the response is a well-formed page with an empty result set and a
   total of zero.
2. **Given** three users exist, **When** a caller lists users without
   specifying a page, **Then** the response contains all three records
   on the first page with a total of three.
3. **Given** thirty users exist and the page size is the system default,
   **When** a caller lists page 1, **Then** the response contains the
   first batch of records and reports a total of thirty; **When** the
   caller lists page 2, **Then** the response contains the next batch.

---

### User Story 4 — Update a user's name or role (Priority: P2)

An administrator can correct a typo in someone's full name or change
their role between ADMIN and DEVELOPER. Username and email are stable
identifiers and cannot be changed through this endpoint.

**Why this priority**: Important for correctness over time, but not on
the critical path of "establishing who exists." A new user can always be
created and the wrong record deleted instead, so this is a quality-of-life
operation rather than a foundation primitive.

**Independent Test**: Create a user as DEVELOPER, update their role to
ADMIN and their name to a new value. Fetching the user afterwards shows
the new values; the username and email are unchanged.

**Acceptance Scenarios**:

1. **Given** a user with full name `Alice Doe` and role `DEVELOPER`,
   **When** an administrator updates their full name to `Alice Smith`
   and role to `ADMIN`, **Then** the change is acknowledged and a
   subsequent fetch shows the new values.
2. **Given** a user, **When** an administrator submits an update that
   only changes the role, **Then** the role is updated and the full
   name is unchanged.
3. **Given** no user exists with identifier `999999`, **When** an
   administrator tries to update that identifier, **Then** the response
   indicates the resource was not found.
4. **Given** a user, **When** an administrator submits an update with an
   invalid role (something other than `ADMIN` or `DEVELOPER`), **Then**
   the request is rejected with a validation response naming the
   invalid field.

---

### User Story 5 — Delete a user (Priority: P2)

An administrator can remove a user from the system. After deletion the
user is no longer returned by fetch-by-id or list calls.

**Why this priority**: Useful for cleanup and for removing mistakenly-
created records, but the system is functional without it (admins could
just leave records in place until soft-delete arrives in a later
feature).

**Independent Test**: Create a user, delete them by identifier. Fetching
the same identifier afterwards returns not-found, and they no longer
appear in list responses.

**Acceptance Scenarios**:

1. **Given** a user with identifier `id`, **When** an administrator
   deletes that identifier, **Then** the response indicates the
   deletion succeeded with no further body, and a subsequent fetch of
   the same identifier returns not-found.
2. **Given** no user exists with identifier `999999`, **When** an
   administrator tries to delete that identifier, **Then** the response
   indicates the resource was not found.

---

### Edge Cases

- **Whitespace-only fields**: Submitting a full name like `"   "` or
  a username with leading/trailing whitespace is rejected as a
  validation failure, not silently trimmed and accepted.
- **Case sensitivity on uniqueness**: `Alice` and `alice` as usernames
  are treated as the same identity — the second one is rejected as a
  duplicate. Same rule for email addresses.
- **Update with no changes**: An update request that contains no
  modifiable fields is rejected as a validation failure (it would be
  a silent no-op otherwise — confusing for the caller).
- **Updating an immutable field**: An update request that tries to
  change the username or email is rejected with a validation response
  that names the offending field. Username and email are stable
  identifiers in this phase.
- **Listing while creating**: A caller listing users at the same time
  another caller is creating a user receives a well-formed page; the
  new record either appears in the result or it does not, but never
  partially.
- **Deleting a user referenced by a future entity**: Out of scope for
  this phase — there are no tickets, comments, or audit entries yet.
  When those features arrive, they will define their own
  referential-integrity rules.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept a request to create a user with the
  fields `username`, `email`, `fullName`, and `role`. All four are
  required.
- **FR-002**: The system MUST assign a server-generated identifier to
  every newly created user and return it in the response.
- **FR-003**: The system MUST reject creation when the supplied
  `username` is already in use (case-insensitive).
- **FR-004**: The system MUST reject creation when the supplied `email`
  is already in use (case-insensitive).
- **FR-005**: The system MUST validate that `email` is a well-formed
  email address.
- **FR-006**: The system MUST validate that `role` is one of the two
  allowed values: `ADMIN` or `DEVELOPER`. Other values are rejected.
- **FR-007**: The system MUST validate that `username` and `fullName`
  are non-empty after trimming and reject whitespace-only submissions.
- **FR-008**: The system MUST support fetching a single user by their
  identifier. An unknown identifier MUST return a not-found response.
- **FR-009**: The system MUST support listing all users with explicit
  pagination — callers can request a specific page and page size, and
  the response MUST report the total number of records.
- **FR-010**: The system MUST support partial updates to a user's
  `fullName` and `role`. A request that supplies one field MUST update
  only that field.
- **FR-011**: The system MUST reject any update that tries to modify
  `username` or `email` — those are immutable through this endpoint.
- **FR-012**: The system MUST support deleting a user by identifier.
  After deletion, the user MUST NOT appear in fetch-by-id or list
  responses.
- **FR-013**: The system MUST return informative, field-level error
  messages for every validation rejection — a caller can identify which
  field failed and why without reading source code.
- **FR-014**: The system MUST persist users durably — a user created in
  one session is still present after the application is restarted.

### Key Entities

- **User**: A person known to the system. Attributes:
  - `id` — server-generated stable identifier.
  - `username` — unique short handle (case-insensitive). Immutable
    after creation in this phase.
  - `email` — unique email address (case-insensitive). Immutable after
    creation in this phase.
  - `fullName` — display name (mutable).
  - `role` — either `ADMIN` or `DEVELOPER` (mutable).
  - `createdAt`, `updatedAt` — system-managed timestamps recording when
    the record was created and last modified.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A caller can create a brand-new user and read it back by
  identifier in a single round-trip, with the returned record matching
  what was submitted, in 100% of valid submissions.
- **SC-002**: Duplicate-username and duplicate-email attempts are
  rejected with a clear, field-naming message in 100% of cases — no
  duplicate user ever ends up persisted.
- **SC-003**: Invalid submissions (bad email format, unknown role,
  whitespace-only fields) are rejected with a message that names the
  offending field and the rule violated in 100% of cases.
- **SC-004**: A list call against a dataset of 30 users returns the
  full set across exactly two pages of 20 + 10 (with the default page
  size), with the reported total equal to the actual count.
- **SC-005**: Updating only the role of a user leaves every other field
  unchanged in 100% of cases. Updating only the full name leaves every
  other field unchanged in 100% of cases.
- **SC-006**: Deleting a user removes them from subsequent fetch and
  list responses in 100% of cases.
- **SC-007**: Created users survive an application restart — every
  record persisted in one process is still readable from the next.

## Assumptions

- **No authentication or authorization yet.** Every endpoint in this
  feature is open. Securing the user-management surface is a later
  feature.
- **Hard delete, not soft delete.** When a user is deleted in this
  phase they are gone — there is no restore endpoint. The
  soft-delete-and-restore pattern that the README describes for
  tickets and projects is a separate concern and may be retro-fitted
  to users later.
- **Username and email are case-insensitive for uniqueness checks**,
  but stored in the original casing the caller supplied. Lookups are
  by identifier, not by username or email, so this only matters at
  creation time.
- **Default page size is 20; maximum page size is 100.** Callers may
  request a smaller page, but cannot bypass the cap.
- **Username constraints**: 3–50 characters, alphanumerics plus dot,
  underscore, and hyphen. (Reasonable web-application default — picked
  so usernames are URL-safe.)
- **Full-name constraints**: 1–100 characters after trimming. Permits
  the vast majority of real names without becoming a free-form text
  field.
- **The two roles are fixed at `ADMIN` and `DEVELOPER`.** Adding a
  third role would be a new feature, not a configuration tweak.
- **No bulk-create or bulk-delete endpoints** in this phase — a single
  user at a time. Bulk import lives with the larger CSV-import story
  in a future feature.

## Out of Scope

- Authentication (login, password storage, sessions, JWTs) — a separate
  feature.
- Authorization rules (who is allowed to create vs. delete users) —
  blocked on authentication.
- Self-service account management ("I want to change my own name") —
  belongs with authentication.
- Soft-delete + restore for users — could be added later; not in this
  phase.
- Search / filtering on the list endpoint (by role, by partial
  username, etc.) — list returns the full paginated set; filtering is
  its own feature.
- Avatar images, contact info beyond email, profile pages — not part
  of "who exists."
- Username or email *change* after creation — explicitly excluded by
  FR-011.
- Referential-integrity rules (cascading deletes, blocking deletion
  when a user owns tickets) — no other entities reference users yet.
