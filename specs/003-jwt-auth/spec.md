# Feature Specification: JWT Authentication & Authorization

**Feature Branch**: `003-jwt-auth`  
**Created**: 2026-05-21  
**Status**: Draft  
**Input**: User description: "Lock the system down. From this phase forward, every endpoint except login requires the caller to first log in with a username and password, receive a token, and send that token with every subsequent request. Tokens expire after 12 minutes — if someone steals a token, the damage window is small. There's a 'who am I' endpoint that returns the current user. Logout is a no-op for now since tokens are stateless; we'll address proper logout in the final phase."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Login and Receive Token (Priority: P1)

A caller submits valid credentials (username + password) and receives a signed JWT they can attach to subsequent requests.

**Why this priority**: All other protected API access depends on this. Without a working login endpoint, nothing else in the auth system can be tested or used.

**Independent Test**: POST /auth/login with correct credentials → 200 OK with `accessToken`, `tokenType`, and `expiresIn` fields. This delivers immediate value: the system is no longer fully open.

**Acceptance Scenarios**:

1. **Given** a user exists with a known username and matching password hash, **When** `POST /auth/login` is called with `{ "username": "jdoe", "password": "secret" }`, **Then** the response is `200 OK` with `{ "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 720 }`.
2. **Given** a user submits wrong credentials, **When** `POST /auth/login` is called, **Then** the response is `401 Unauthorized`.
3. **Given** a user submits missing fields, **When** `POST /auth/login` is called, **Then** the response is `400 Bad Request`.

---

### User Story 2 - Access Protected Endpoints with Token (Priority: P1)

After receiving a token from login, the caller includes it as `Authorization: Bearer <token>` on every request and the system grants access.

**Why this priority**: This is the core security enforcement. Without it, the login endpoint is decorative.

**Independent Test**: Take a token from Story 1, attach it to `GET /users`, and observe `200 OK`. Without the token, the same request returns `401 Unauthorized`.

**Acceptance Scenarios**:

1. **Given** a valid unexpired token, **When** any protected endpoint is called with `Authorization: Bearer <token>`, **Then** the request proceeds normally.
2. **Given** no `Authorization` header, **When** any protected endpoint (except `/auth/login`) is called, **Then** the response is `401 Unauthorized`.
3. **Given** a malformed or expired token, **When** a protected endpoint is called, **Then** the response is `401 Unauthorized`.

---

### User Story 3 - Identify Current User (Priority: P2)

An authenticated caller can ask "who am I?" and receive their own user profile.

**Why this priority**: Useful for clients to display current user info and validate their session. Depends on Story 2 but is independently demonstrable.

**Independent Test**: With a valid token, `GET /auth/me` returns the full user profile DTO matching the authenticated user.

**Acceptance Scenarios**:

1. **Given** a valid token for user "jdoe", **When** `GET /auth/me` is called, **Then** the response is `200 OK` with `{ "id": 1, "username": "jdoe", "email": "...", "fullName": "...", "role": "DEVELOPER" }`.
2. **Given** no valid token, **When** `GET /auth/me` is called, **Then** the response is `401 Unauthorized`.

---

### User Story 4 - Logout (No-Op) (Priority: P3)

A caller can POST to `/auth/logout` and receive a success response, even though no server-side token revocation occurs in this phase.

**Why this priority**: Satisfies the API contract and prepares clients for real revocation in a future phase. Lowest risk — it always succeeds.

**Independent Test**: `POST /auth/logout` (with or without a token) returns `200 OK` with an empty body.

**Acceptance Scenarios**:

1. **Given** any authenticated request, **When** `POST /auth/logout` is called, **Then** the response is `200 OK` with an empty body.

---

### Edge Cases

- What happens when a token's expiry timestamp is exactly at the current time? → treated as expired → `401`.
- What happens when the JWT secret is blank or missing from configuration? → application startup fails fast with a clear error.
- What happens when a user referenced in the token's `userId` claim no longer exists in the database? → `401 Unauthorized` (principal cannot be resolved).
- What happens when the `Authorization` header is present but not in `Bearer <token>` format? → `401 Unauthorized`.
- What happens when the token is valid but the `exp` claim has passed? → `401 Unauthorized`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose `POST /auth/login` accepting `{ "username", "password" }` and returning `{ "accessToken", "tokenType", "expiresIn" }` on success.
- **FR-002**: The system MUST issue signed tokens with a configurable expiry (default 12 minutes) that clients send as `Authorization: Bearer <token>`.
- **FR-003**: The system MUST reject any request to a protected endpoint that does not carry a valid, unexpired token with `401 Unauthorized`.
- **FR-004**: The system MUST expose `GET /auth/me` returning the current user's profile (id, username, email, fullName, role) when a valid token is present.
- **FR-005**: The system MUST expose `POST /auth/logout` returning `200 OK` with no body; revocation is deferred to a future phase.
- **FR-006**: Tokens MUST embed the subject (username), userId, and role claims so that the system can authorize without an additional database lookup in later phases.
- **FR-007**: Token expiry MUST be configurable via application configuration without code changes.
- **FR-008**: The system MUST use industry-standard password hashing (bcrypt) to verify credentials against the stored hash.
- **FR-009**: Every endpoint except `POST /auth/login` MUST require authentication.
- **FR-010**: The authorization model MUST use plain role names (e.g., `ADMIN`, `DEVELOPER`) as authority strings — no `ROLE_` prefix — so the same string appears in the database, the token, and any future permission check.

### Key Entities *(include if feature involves data)*

- **AuthToken**: Transient (never persisted); carries `accessToken` (signed string), `tokenType` ("Bearer"), and `expiresIn` (seconds until expiry).
- **LoginRequest**: Input DTO; `username` and `password` fields.
- **CurrentUserResponse**: Output DTO; mirrors `UserResponse` — id, username, email, fullName, role.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A caller with valid credentials can obtain a token and use it to access any protected endpoint within one round-trip login.
- **SC-002**: Any request to a protected endpoint without a valid token is rejected within the same response cycle (no partial processing).
- **SC-003**: Tokens become invalid no more than 12 minutes after issuance, limiting the exposure window if a token is intercepted.
- **SC-004**: The login endpoint rejects bad credentials with `401` in 100% of cases tested (no false positives).
- **SC-005**: The `/auth/me` endpoint returns the correct user profile for every authenticated caller — no data leakage across users.
- **SC-006**: All existing endpoints (e.g., `/users`, `/health`) continue to function correctly when called with a valid token (no regressions).

## Assumptions

- Users already exist in the database with a `passwordHash` column (added in the Users CRUD phase). New users must have their password set via the `POST /users` endpoint (or directly in the DB during this phase).
- Token signing uses a symmetric secret (HS256); asymmetric signing (RS256) is out of scope.
- The logout endpoint is intentionally a no-op in this phase; token blacklisting / refresh tokens are a later-phase concern.
- `/health` (actuator) may remain open or require auth — we treat it as open (accessible without a token) to support liveness checks.
- Method-level authorization annotations (`@PreAuthorize`) are wired but no rules are enforced in this phase; they will be activated in subsequent phases.
- All password verification is done against the bcrypt hash stored in `users.password_hash`; plain-text storage is never acceptable.
