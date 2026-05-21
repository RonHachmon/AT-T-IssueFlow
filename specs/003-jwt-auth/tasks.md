# Tasks: JWT Authentication & Authorization

**Input**: Design documents from `specs/003-jwt-auth/`  
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [data-model.md](data-model.md), [contracts/auth.openapi.yaml](contracts/auth.openapi.yaml), [research.md](research.md)

**Organization**: Tasks are grouped by phase. Phase 2 is foundational (security infrastructure) and must complete before user story phases can be validated end-to-end. Unit tests are included per constitution requirements (Principle II).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no shared dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)

---

## Phase 1: Setup

**Purpose**: Dependency wiring, configuration, and seed data — no code compiles against Spring Security until this is done.

- [X] T001 Update `pom.xml`: remove `spring-security-crypto` direct dependency; add `spring-boot-starter-security`, `jjwt-api:0.12.6` (compile), `jjwt-impl:0.12.6` (runtime), `jjwt-jackson:0.12.6` (runtime)
- [X] T002 [P] Add JWT config block to `src/main/resources/application.yaml`: `app.security.jwt.secret` (env-var backed) and `app.security.jwt.access-token-ttl: PT12M`
- [X] T003 [P] Create `src/main/resources/db/migration/V3__seed_test_users.sql`: INSERT admin (ADMIN role, password `Admin1234!`) and developer (DEVELOPER role, password `Dev1234!`) with BCrypt-hashed `password_hash` values; use `ON CONFLICT DO NOTHING`

**Checkpoint**: `./mvnw compile` succeeds with Spring Security on the classpath and Flyway picks up V3 migration on next startup.

---

## Phase 2: Foundational (Security Infrastructure)

**Purpose**: Core security plumbing that every user story depends on. No user story can be validated end-to-end until this phase is complete.

**⚠️ CRITICAL**: Phase 3 and later phases require this phase to be fully complete.

- [X] T004 Create `src/main/java/com/att/tdp/issueflow/common/security/JwtProperties.java`: `@ConfigurationProperties(prefix = "app.security.jwt")` record with `String secret` and `Duration accessTokenTtl`; annotate `IssueFlowApplication` with `@EnableConfigurationProperties(JwtProperties.class)`
- [X] T005 [P] Create `src/main/java/com/att/tdp/issueflow/common/security/JwtService.java`: `@Service`; derive `SecretKey` from `JwtProperties.secret()` via `Keys.hmacShaKeyFor`; implement `issueToken(UserDetails, Long userId, String role)`, `validateToken(String)`, `extractUsername(String)`, `extractUserId(String)`, `extractRole(String)`; private `parseClaims(String)` helper; Javadoc on all public methods
- [X] T006 [P] Add `Optional<User> findByUsernameIgnoreCase(String username)` to `src/main/java/com/att/tdp/issueflow/user/UserRepository.java` with Javadoc
- [X] T007 Create `src/main/java/com/att/tdp/issueflow/auth/IssueFlowUserDetailsService.java`: `@Service implements UserDetailsService`; `loadUserByUsername` calls `userRepository.findByUsernameIgnoreCase`; builds `UserDetails` with `new SimpleGrantedAuthority(user.getRole().name())` (no `ROLE_` prefix); falls back to empty string if `passwordHash` is null; Javadoc on public method
- [X] T008 Create `src/main/java/com/att/tdp/issueflow/common/security/JwtAuthenticationFilter.java`: `@Component extends OncePerRequestFilter`; private `extractBearerToken(HttpServletRequest)` strips `Authorization: Bearer ` prefix; private `authenticateRequest(String, HttpServletRequest)` validates token, loads `UserDetails`, sets `UsernamePasswordAuthenticationToken` on `SecurityContextHolder`; `doFilterInternal` calls both and always invokes `chain.doFilter`; Javadoc on public method
- [X] T009 Create `src/main/java/com/att/tdp/issueflow/common/security/SecurityConfiguration.java`: `@Configuration @EnableMethodSecurity`; `SecurityFilterChain` bean — CSRF disabled, `STATELESS` session, `requestMatchers("/auth/login", "/health/**", "/actuator/health/**").permitAll()`, all else authenticated, `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`, custom `AuthenticationEntryPoint` returning RFC 7807 JSON with status 401; `DaoAuthenticationProvider` bean wired with `IssueFlowUserDetailsService` + `PasswordEncoder`; `AuthenticationManager` bean via `AuthenticationConfiguration`; Javadoc on all beans
- [X] T010 [P] Create `src/test/java/com/att/tdp/issueflow/auth/JwtServiceTest.java`: pure-JVM tests (no Spring context, no `@SpringBootTest`) — `tokenContainsExpectedSubjectUserIdAndRoleClaims`, `validTokenPassesValidation`, `expiredTokenIsRejected`, `tokenWithTamperedSignatureIsRejected`; instantiate `JwtService` directly with a test `JwtProperties`; follow Arrange-Act-Assert with blank-line separation
- [X] T011 [P] Create `src/test/java/com/att/tdp/issueflow/auth/IssueFlowUserDetailsServiceTest.java`: pure-JVM tests — `returnsUserDetailsForExistingUsername` (mock `UserRepository`, assert username + authority string `DEVELOPER`), `throwsUsernameNotFoundExceptionWhenUserDoesNotExist` (mock returns `Optional.empty()`, assert `UsernameNotFoundException`); instantiate `IssueFlowUserDetailsService` directly

**Checkpoint**: `./mvnw test` passes (JwtServiceTest + IssueFlowUserDetailsServiceTest run). After app startup, `GET /users` without a token returns `401`; `GET /health` returns `200`.

---

## Phase 3: User Story 1 — Login and Receive Token + User Story 2 — Access Protected Endpoints (Priority: P1) 🎯 MVP

**Goal**: A caller submits credentials and receives a signed JWT. That JWT grants access to all protected endpoints; requests without it are rejected.

**Independent Test**: `POST /auth/login` with seed credentials → `200 OK` with `accessToken`; `GET /users` with the token → `200 OK`; `GET /users` without the token → `401 Unauthorized`.

- [X] T012 [P] [US1] Create `src/main/java/com/att/tdp/issueflow/auth/dto/LoginRequest.java`: record with `@NotBlank String username` and `@NotBlank String password`; Javadoc
- [X] T013 [P] [US1] Create `src/main/java/com/att/tdp/issueflow/auth/dto/TokenResponse.java`: record with `String accessToken`, `String tokenType`, `long expiresIn`; Javadoc
- [X] T014 [US1] Create `src/main/java/com/att/tdp/issueflow/auth/AuthController.java` with the `login()` method only: `@RestController @RequestMapping("/auth")`; inject `AuthenticationManager`, `JwtService`, `JwtProperties`, `UserRepository`, `UserMapper`; `@PostMapping("/login")` calls `authenticationManager.authenticate(...)`, loads `User` entity via `userRepository.findByUsernameIgnoreCase`, calls `jwtService.issueToken`, returns `TokenResponse` with `expiresIn = jwtProperties.accessTokenTtl().toSeconds()`; Javadoc on public methods
- [X] T015 [US1] Add `Authentication` folder to `postman/issueflow.postman_collection.json` with three login requests: `Login — Valid Credentials` (POST /auth/login, stores `authToken` env var, asserts 200 + `accessToken`/`tokenType`/`expiresIn` fields present), `Login — Wrong Password` (asserts 401), `Login — Missing Fields` (body `{}`, asserts 400)
- [X] T016 [US2] Add two protected-endpoint requests to the `Authentication` Postman folder: `List Users — With Token` (GET /users with `Authorization: Bearer {{authToken}}`, asserts 200), `List Users — No Token` (GET /users no header, asserts 401)

**Checkpoint**: US1 fully functional — login returns a valid JWT. US2 verified — protected endpoints enforce authentication. US1 and US2 are both independently testable from Postman.

---

## Phase 4: User Story 3 — Identify Current User (Priority: P2)

**Goal**: An authenticated caller can retrieve their own user profile via `GET /auth/me`.

**Independent Test**: With a token from the Phase 3 login, `GET /auth/me` returns `200 OK` with the correct `id`, `username`, `email`, `fullName`, and `role`.

- [X] T017 [US3] Add `me()` method to `src/main/java/com/att/tdp/issueflow/auth/AuthController.java`: `@GetMapping("/me") @ResponseStatus(HttpStatus.OK)`; parameter `@AuthenticationPrincipal UserDetails principal`; calls `userRepository.findByUsernameIgnoreCase(principal.getUsername()).orElseThrow()`; returns `userMapper.toResponse(user)`; Javadoc
- [X] T018 [US3] Add `Get Current User (/me)` request to the `Authentication` Postman folder: `GET /auth/me` with `Authorization: Bearer {{authToken}}`; assert status 200; assert `id`, `username`, `email`, `fullName`, `role` all present

**Checkpoint**: US3 fully functional — `GET /auth/me` returns the current user's profile for any authenticated caller.

---

## Phase 5: User Story 4 — Logout No-Op (Priority: P3)

**Goal**: `POST /auth/logout` returns `200 OK` with an empty body. No server-side action taken; documented as a no-op pending real revocation.

**Independent Test**: `POST /auth/logout` (authenticated) returns `200 OK` with no body content.

- [X] T019 [US4] Add `logout()` method to `src/main/java/com/att/tdp/issueflow/auth/AuthController.java`: `@PostMapping("/logout") @ResponseStatus(HttpStatus.OK)`; `void` return; method body is empty with a Javadoc comment noting this is a stateless no-op and real revocation is deferred to the final auth phase
- [X] T020 [US4] Add `Logout` request to the `Authentication` Postman folder: `POST /auth/logout` with `Authorization: Bearer {{authToken}}`; assert status 200; assert response body is empty

**Checkpoint**: All four user stories are functional and independently testable via Postman.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, README accuracy, decision records, and constitution compliance sign-off.

- [X] T021 [P] Create `docs/decisions.md`: document the authority-model decision — plain role names (`ADMIN`, `DEVELOPER`), no `ROLE_` prefix, built via `new SimpleGrantedAuthority(user.getRole().name())`; note that future `@PreAuthorize` expressions must use `hasAuthority('ADMIN')` not `hasRole('ADMIN')`; explain rationale (same string in DB, JWT claim, security expression)
- [X] T022 [P] Update `README.md` Authentication APIs table: fill in the `GET /auth/me` response body column with `{ "id": 1, "username": "jdoe", "email": "jdoe@example.com", "fullName": "John Doe", "role": "DEVELOPER" }`
- [X] T023 [P] Update `run.md`: add section for `APP_SECURITY_JWT_SECRET` environment variable (required, minimum 32 characters, `openssl rand -hex 32` to generate), seed user credentials (admin / Admin1234!, developer / Dev1234!), note that tokens expire in 12 minutes (configurable via `app.security.jwt.access-token-ttl`)
- [X] T024 [P] Add entry to `prompts.md`: record model (claude-sonnet-4-6), date (2026-05-21), and the key prompts that produced the security plan and implementation tasks for this feature
- [X] T025 Run `./mvnw verify` end-to-end and confirm all unit tests pass; run Postman Authentication folder via Newman (`npx newman run postman/issueflow.postman_collection.json --environment postman/issueflow.local.postman_environment.json`) and confirm all 7 auth requests pass

**Checkpoint**: All constitution gates satisfied — Javadoc present, README updated, run.md accurate, prompts.md honest, decisions.md written.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 completion (Spring Security must be on classpath); blocks Phase 3
- **Phase 3 (US1 + US2)**: Depends on Phase 2 completion
- **Phase 4 (US3)**: Depends on Phase 2 completion; requires Phase 3 for the Postman token to be available
- **Phase 5 (US4)**: Depends on Phase 2 completion; can run in parallel with Phase 4
- **Phase 6 (Polish)**: Depends on all user story phases being complete

### User Story Dependencies

- **US1 + US2 (Phase 3, P1)**: Requires Phase 2. No dependency on US3/US4.
- **US3 (Phase 4, P2)**: Requires Phase 2. The Postman test uses the token stored by US1's Postman request, so Phase 3 must run first in Postman test sessions.
- **US4 (Phase 5, P3)**: Requires Phase 2. Can be implemented in parallel with US3.

### Within Each Phase

- Models / DTOs before services / controllers that reference them
- `JwtProperties` (T004) before `JwtService` (T005) — `JwtService` depends on it at runtime (not compile time, so they can be coded in parallel but Spring wiring needs T004 present)
- `UserRepository.findByUsernameIgnoreCase` (T006) before `IssueFlowUserDetailsService` compiles cleanly (T007)
- `JwtService` (T005) + `IssueFlowUserDetailsService` (T007) before `JwtAuthenticationFilter` (T008)
- `JwtAuthenticationFilter` (T008) + `IssueFlowUserDetailsService` (T007) before `SecurityConfiguration` (T009)
- Unit tests (T010, T011) can be written in parallel with corresponding implementations

### Parallel Opportunities

| Parallel Group | Tasks |
|----------------|-------|
| Phase 1 config + migration | T002, T003 (after T001 is done) |
| Phase 2 core services | T005, T006 (both have only T004 as prerequisite) |
| Phase 2 unit tests | T010, T011 (write alongside T005, T007) |
| Phase 3 DTOs | T012, T013 (both are standalone records) |
| Phase 5 + Phase 4 | T019, T020 can run in parallel with T017, T018 |
| Phase 6 docs | T021, T022, T023, T024 (all independent files) |

---

## Parallel Example: Phase 2

```
# Start these together after T004 (JwtProperties) is created:
Task T005: JwtService.java
Task T006: UserRepository.java (add finder)

# Once T005 + T006 + T007 are done, start:
Task T008: JwtAuthenticationFilter.java

# Start in parallel with T005/T006/T007/T008:
Task T010: JwtServiceTest.java
Task T011: IssueFlowUserDetailsServiceTest.java
```

---

## Implementation Strategy

### MVP First (US1 + US2 — Phases 1–3)

1. Complete **Phase 1**: Dependencies, config, seed migration
2. Complete **Phase 2**: Security infrastructure (JwtProperties → JwtService → UserDetailsService → Filter → SecurityConfig)
3. Complete **Phase 3**: Login endpoint + DTOs + Postman login/protection tests
4. **STOP and VALIDATE**: `./mvnw test` passes; Postman shows login → 200 and unauth → 401
5. The system is now locked down — this is the core deliverable

### Incremental Delivery

1. Phases 1–3 → **MVP locked** (login + all endpoints protected)
2. Phase 4 → **US3** (/auth/me — caller identity)
3. Phase 5 → **US4** (logout no-op endpoint)
4. Phase 6 → **Polish** (docs, README, run.md)

---

## Notes

- `[P]` tasks touch different files — safe to work on simultaneously
- All unit tests are **pure JVM** (no `@SpringBootTest`, no `@DataJpaTest`) — fast and design-forcing
- The BCrypt hashes in T003 are pre-computed at strength 10; regenerate them if the encoder strength is changed
- `PasswordEncoderConfiguration` in `common/security/` is **unchanged** — do not modify it
- `@EnableMethodSecurity` on `SecurityConfiguration` activates `@PreAuthorize` for later phases; no method-level rules are enforced yet in this feature
