# Implementation Plan: JWT Authentication & Authorization

**Branch**: `003-jwt-auth` | **Date**: 2026-05-21 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `specs/003-jwt-auth/spec.md`

## Summary

Add stateless JWT-based authentication to IssueFlow. The system issues signed HS256 tokens on `POST /auth/login`, validates them on every incoming request via a servlet filter, and exposes two additional auth endpoints (`POST /auth/logout` as a no-op, `GET /auth/me` returning the caller's profile). All endpoints except `/auth/login` and `/health` require a valid bearer token from this phase forward. The authorization model uses plain role names (`ADMIN`, `DEVELOPER`) with no `ROLE_` prefix so the same string appears in the database, the JWT claim, and any future `@PreAuthorize` expression.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.4.2  
**Primary Dependencies**: spring-boot-starter-security (Spring Security 6.4.x), io.jsonwebtoken:jjwt-api / jjwt-impl / jjwt-jackson 0.12.6  
**Storage**: PostgreSQL — no schema change; `password_hash` column already exists from `V2__users.sql`. New Flyway migration `V3__seed_test_users.sql` inserts dev/test users with bcrypt-hashed passwords.  
**Testing**: JUnit 5 + Mockito (pure JVM, no Spring context); Postman collection with new Auth folder  
**Target Platform**: Linux container / local JVM (single-node Spring Boot app)  
**Project Type**: REST web service  
**Performance Goals**: JWT filter overhead < 5 ms per request  
**Constraints**: Tokens expire in 12 minutes (configurable via ISO-8601 duration `PT12M`); stateless (no `HttpSession`)  
**Scale/Scope**: Single-node; no distributed token validation

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Gate | Pass? |
|---|------|-------|
| I  | **Clean Code** — `JwtService` has clear single-purpose methods (`issueToken`, `validateToken`, `extractUsername`, etc.). `JwtAuthenticationFilter` delegates to `JwtService`; no mixed abstraction levels. No flag arguments. Role string derived via `user.getRole().name()` — intent clear. No magic strings; all literals are named constants or config-bound values. | [x] |
| II | **Testing Standards** — Pure-JVM unit tests: `JwtServiceTest` (sign/validate/expiry/tamper), `IssueFlowUserDetailsServiceTest` (happy path + not-found). Postman: login-happy, login-bad-credentials, login-missing-fields, access-protected-with-token, access-protected-without-token, `/auth/me`, `/auth/logout`. | [x] |
| III| **Documentation Discipline** — Javadoc on every new public method. README `/auth` table updated (`GET /auth/me` response body filled in). `run.md` updated with `APP_SECURITY_JWT_SECRET` env var and seed user credentials. `prompts.md` entry added. `docs/decisions.md` created documenting the no-ROLE_-prefix authority model. | [x] |
| IV | **API Consistency** — POST /auth/login → 200 OK `{ accessToken, tokenType, expiresIn }` matches README exactly. POST /auth/logout → 200 OK empty body matches README exactly. GET /auth/me → 200 OK with user DTO. 401 responses use RFC 7807 Problem Detail via custom `AuthenticationEntryPoint`. | [x] |

## Project Structure

### Documentation (this feature)

```text
specs/003-jwt-auth/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── auth.openapi.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
pom.xml
  + spring-boot-starter-security
  + io.jsonwebtoken:jjwt-api:0.12.6
  + io.jsonwebtoken:jjwt-impl:0.12.6 (runtime)
  + io.jsonwebtoken:jjwt-jackson:0.12.6 (runtime)
  - spring-security-crypto (now transitive via starter)

src/main/java/com/att/tdp/issueflow/
├── auth/
│   ├── AuthController.java
│   │     POST /auth/login  — authenticate credentials, return TokenResponse
│   │     POST /auth/logout — 200 OK empty body, documented no-op
│   │     GET  /auth/me     — return UserResponse for @AuthenticationPrincipal
│   ├── IssueFlowUserDetailsService.java
│   │     implements UserDetailsService
│   │     loadUserByUsername → findByUsernameIgnoreCase → SimpleGrantedAuthority(role.name())
│   ├── dto/
│   │   ├── LoginRequest.java      (@NotBlank username, @NotBlank password)
│   │   └── TokenResponse.java     (accessToken, tokenType, expiresIn)
│   └── package-info.java          (already exists)
│
└── common/
    └── security/
        ├── PasswordEncoderConfiguration.java  (already exists — NO changes)
        ├── JwtProperties.java
        │     @ConfigurationProperties("app.security.jwt")
        │     record: secret (String), accessTokenTtl (Duration)
        ├── JwtService.java
        │     issueToken(UserDetails, Long userId, String role) → String
        │     validateToken(String) → boolean
        │     extractUsername(String) → String
        │     extractUserId(String) → Long
        │     extractRole(String) → String
        ├── JwtAuthenticationFilter.java
        │     extends OncePerRequestFilter
        │     extractBearerToken(request) → String|null
        │     authenticateRequest(token, request) → populates SecurityContextHolder
        └── SecurityConfiguration.java
              @Configuration @EnableMethodSecurity
              SecurityFilterChain bean:
                csrf disabled, STATELESS session
                /auth/login + /health/** + /actuator/health/** → permitAll
                all other paths → authenticated
                JwtAuthenticationFilter before UsernamePasswordAuthenticationFilter
                custom AuthenticationEntryPoint → RFC 7807, 401
              AuthenticationManager bean
              AuthenticationProvider bean (DaoAuthenticationProvider)

src/main/resources/
├── application.yaml               (+ app.security.jwt block)
└── db/migration/
    └── V3__seed_test_users.sql    (INSERT admin/developer with bcrypt hashes)

src/test/java/com/att/tdp/issueflow/
└── auth/
    ├── JwtServiceTest.java
    │     tokenIssuedWithCorrectClaims
    │     validTokenPassesValidation
    │     expiredTokenFailsValidation
    │     tamperedTokenFailsValidation
    └── IssueFlowUserDetailsServiceTest.java
          loadsExistingUserByUsername
          throwsUsernameNotFoundExceptionForUnknownUser

postman/
└── issueflow.postman_collection.json  (+ "Authentication" folder, 7 requests)

docs/
└── decisions.md                       (authority model: no ROLE_ prefix)

README.md       (GET /auth/me response body column filled in)
run.md          (APP_SECURITY_JWT_SECRET section + seed user credentials)
prompts.md      (this planning session)
```

**Structure Decision**: Single-project layout (existing). Auth domain classes live in `auth/`; security infrastructure (filter, JWT service, config, properties) lives in `common/security/` — consistent with the `PasswordEncoderConfiguration` already there.

---

## Implementation Guide

### Step 1 — pom.xml: Replace `spring-security-crypto` with full starter + add jjwt

Remove:
```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

Add (in the dependencies block):
```xml
<!-- Spring Security 6 — pulls spring-security-crypto transitively -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT: api on compile path; impl + jackson runtime-only -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

### Step 2 — application.yaml: Add JWT config block

Append to `application.yaml`:
```yaml
app:
  security:
    jwt:
      secret: ${APP_SECURITY_JWT_SECRET:change-me-in-production-use-32-plus-chars}
      access-token-ttl: PT12M
```

### Step 3 — V3__seed_test_users.sql

```sql
-- Seed users for development and testing.
-- Passwords (BCrypt strength 10):
--   admin     / Admin1234!
--   developer / Dev1234!
-- Regenerate hashes with BCryptPasswordEncoder if encoder strength changes.
INSERT INTO users (username, email, full_name, role, password_hash)
VALUES
  ('admin',
   'admin@issueflow.local',
   'Admin User',
   'ADMIN',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'),
  ('developer',
   'developer@issueflow.local',
   'Dev User',
   'DEVELOPER',
   '$2a$10$c6bON1nU.GxpwK3HCDKkPO5HGn2Wp1qEqjHKxkWPGwrRtjy1ziqjm')
ON CONFLICT DO NOTHING;
```

### Step 4 — JwtProperties.java

`@ConfigurationProperties` record binding `app.security.jwt.*`. Register via
`@EnableConfigurationProperties(JwtProperties.class)` on `IssueFlowApplication` (or a
`@Configuration` class).

```java
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl) {}
```

### Step 5 — JwtService.java

`@Service` that holds a `SecretKey` derived from `JwtProperties.secret()` via
`Keys.hmacShaKeyFor(...)`. Public API:

| Method | Signature | Notes |
|--------|-----------|-------|
| `issueToken` | `(UserDetails, Long userId, String role) → String` | Sets `sub`, `userId`, `role`, `iat`, `exp` |
| `validateToken` | `(String) → boolean` | Returns `false` on any `JwtException` or parse error |
| `extractUsername` | `(String) → String` | Reads `sub` claim |
| `extractUserId` | `(String) → Long` | Reads `userId` claim |
| `extractRole` | `(String) → String` | Reads `role` claim |

Private helper `parseClaims(String) → Claims` shared by all extract methods.

### Step 6 — JwtAuthenticationFilter.java

`@Component extends OncePerRequestFilter`. Two private methods:

- `extractBearerToken(HttpServletRequest) → String|null` — reads `Authorization` header, strips `"Bearer "` prefix, returns `null` if absent or wrong format.
- `authenticateRequest(String token, HttpServletRequest)` — calls `jwtService.validateToken`, loads `UserDetails`, constructs `UsernamePasswordAuthenticationToken`, populates `SecurityContextHolder`.

`doFilterInternal` calls `extractBearerToken`; if non-null and valid, calls `authenticateRequest`; always calls `chain.doFilter`.

### Step 7 — IssueFlowUserDetailsService.java

`@Service implements UserDetailsService`.  
`loadUserByUsername(String username)`:
1. `userRepository.findByUsernameIgnoreCase(username).orElseThrow(UsernameNotFoundException::new)`
2. Return `new User(user.getUsername(), passwordHash, List.of(new SimpleGrantedAuthority(user.getRole().name())))`
   - `passwordHash` falls back to `""` if null (prevents NPE; Spring Security treats blank hash as non-matching)

### Step 8 — UserRepository.java: Add finder

```java
Optional<User> findByUsernameIgnoreCase(String username);
```

Javadoc: lookup used by `IssueFlowUserDetailsService` and auth endpoints; returns the user whose username matches case-insensitively.

### Step 9 — SecurityConfiguration.java

`@Configuration @EnableMethodSecurity`. Beans:

1. **`SecurityFilterChain`** — configures `HttpSecurity`:
   - `csrf.disable()`
   - `sessionManagement` → `STATELESS`
   - `authorizeHttpRequests`: permit `/auth/login`, `/health/**`, `/actuator/health/**`; authenticate everything else
   - `authenticationProvider(authenticationProvider())`
   - `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
   - `exceptionHandling` → custom `AuthenticationEntryPoint` returning RFC 7807 JSON, status 401

2. **`AuthenticationProvider`** — `DaoAuthenticationProvider` wired with `IssueFlowUserDetailsService` + `PasswordEncoder`.

3. **`AuthenticationManager`** — delegated from `AuthenticationConfiguration.getAuthenticationManager()`.

### Step 10 — AuthController.java

`@RestController @RequestMapping("/auth")`. Three endpoints:

| Method | Path | Logic |
|--------|------|-------|
| `POST` | `/login` | `authenticationManager.authenticate(...)` → load `User` entity → `jwtService.issueToken(...)` → return `TokenResponse` |
| `POST` | `/logout` | `void` — documented no-op; returns 200 |
| `GET`  | `/me` | `@AuthenticationPrincipal UserDetails` → load `User` entity → `userMapper.toResponse(user)` |

`LoginRequest` and `TokenResponse` are new DTOs in `auth/dto/`. `UserResponse` and `UserMapper` are reused from the `user` package.

### Step 11 — LoginRequest.java + TokenResponse.java

**LoginRequest** (record):
```java
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
```

**TokenResponse** (record):
```java
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
```

### Step 12 — docs/decisions.md

Create `docs/decisions.md` documenting the authority-model decision:

> **Authority strings use no `ROLE_` prefix.** Authorities are built as
> `new SimpleGrantedAuthority(user.getRole().name())`, producing `ADMIN` or `DEVELOPER`.
> Future `@PreAuthorize` expressions must use `hasAuthority('ADMIN')`, not `hasRole('ADMIN')`.
> This keeps the same string visible in the database (`role` column), the JWT `role` claim,
> and the security expression — no hidden prefix convention to explain or debug.

### Step 13 — Unit Tests

**`JwtServiceTest`** (`src/test/java/.../auth/JwtServiceTest.java`):

Test names as sentences:
- `tokenContainsExpectedSubjectUserIdAndRoleClaims` — issue a token; parse manually; assert all three claims.
- `validTokenPassesValidation` — issue → `validateToken` → `true`.
- `expiredTokenIsRejected` — issue with `PT-1S` TTL (past expiry via negative duration or `Date.from(Instant.now().minusSeconds(1))`); `validateToken` → `false`.
- `tokenWithTamperedSignatureIsRejected` — append `"x"` to compact token; `validateToken` → `false`.

**`IssueFlowUserDetailsServiceTest`** (`src/test/java/.../auth/IssueFlowUserDetailsServiceTest.java`):

- `returnsUserDetailsForExistingUsername` — mock `UserRepository`; assert username + authority string `DEVELOPER`.
- `throwsUsernameNotFoundExceptionWhenUserDoesNotExist` — mock returns `Optional.empty()`; expect `UsernameNotFoundException`.

### Step 14 — Postman: Authentication folder

Add a new `Authentication` folder to `issueflow.postman_collection.json` with 7 requests. Earlier requests should store the token in an environment variable (`pm.environment.set("authToken", response.accessToken)`) so downstream requests can use `{{authToken}}`.

| # | Name | Method | Path | Expected Status |
|---|------|--------|------|-----------------|
| 1 | Login — Valid Credentials | POST | /auth/login | 200 (stores token) |
| 2 | Login — Wrong Password | POST | /auth/login | 401 |
| 3 | Login — Missing Fields | POST | /auth/login | 400 |
| 4 | List Users — With Token | GET | /users | 200 |
| 5 | List Users — No Token | GET | /users | 401 |
| 6 | Get Current User (/me) | GET | /auth/me | 200 |
| 7 | Logout | POST | /auth/logout | 200 |

### Step 15 — README.md: Fill in GET /auth/me response body

The README Authentication APIs table has a blank response body for `GET /auth/me`. Fill it in:

```
{ "id": 1, "username": "jdoe", "email": "jdoe@example.com", "fullName": "John Doe", "role": "DEVELOPER" }
```

### Step 16 — run.md: Document JWT secret + seed users

Add a new section covering:
- The `APP_SECURITY_JWT_SECRET` environment variable (required; minimum 32 characters)
- How to generate a secure secret (`openssl rand -hex 32`)
- Seed user credentials (admin / Admin1234!, developer / Dev1234!)
- Note that tokens expire in 12 minutes by default (configurable via `app.security.jwt.access-token-ttl`)

### Step 17 — prompts.md entry

Record model, date, and key prompts used to produce this plan.

---

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No constitution violations in this plan.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `AuthController` injects `UserRepository` directly | Need `User.id` and `User.role` for JWT claims and `/me` response; Spring Security resolves only a `UserDetails` (no entity) after authentication | A dedicated `AuthService` layer is an abstraction without a concrete problem at this scale; a second `UserDetailsService` call would duplicate the DB round-trip |
