# Research: JWT Authentication (003-jwt-auth)

**Branch**: `003-jwt-auth` | **Date**: 2026-05-21

---

## Decision 1: jjwt Library Version

**Decision**: `io.jsonwebtoken:jjwt-api / jjwt-impl / jjwt-jackson` at version **0.12.6**

**Rationale**: 0.12.x is the current stable generation of the JJWT library. It ships a clean fluent API (`Jwts.builder()`, `Jwts.parser()`) that replaces the deprecated 0.11.x surface. Version 0.12.6 is the latest patch as of 2025-Q4. Three-artifact split (api / impl / jackson) follows the library's own recommendation: `api` on compile classpath, `impl` and `jackson` runtime-only so the implementation is swappable.

**Alternatives considered**:
- `nimbus-jose-jwt` (Auth0): production-grade but heavier API and less common in Spring Boot tutorials; JJWT fits the project scope better.
- Spring Security's own JWT support (spring-security-oauth2-resource-server): opinionated and pulls OAuth2 scaffolding we do not need for simple HS256.

---

## Decision 2: Signing Algorithm — HS256

**Decision**: HS256 (HMAC-SHA-256) with a symmetric secret stored in `application.yml` via environment-variable override.

**Rationale**: HS256 is the simplest correct algorithm for a single-server backend with no third-party token consumers. The secret lives in `app.security.jwt.secret` and is injected from an environment variable in production (`APP_SECURITY_JWT_SECRET`). Tokens are not verified by external parties, so asymmetric RS256 adds complexity without benefit at this scale.

**Alternatives considered**:
- RS256: required when multiple services verify the token or when the token is shared with a public audience; overkill here.
- HS512: stronger but requires a 512-bit key and adds no practical security given the 12-minute TTL.

---

## Decision 3: Authority Model — No `ROLE_` Prefix

**Decision**: Authority strings are plain role names (`ADMIN`, `DEVELOPER`). Authorities are built as `new SimpleGrantedAuthority(user.getRole().name())` in `IssueFlowUserDetailsService`. Future `@PreAuthorize` expressions use `hasAuthority('ADMIN')`, **not** `hasRole('ADMIN')`.

**Rationale**: Spring Security's `hasRole()` silently prepends `ROLE_` and compares against `ROLE_ADMIN`. This hidden convention creates a disconnect between the string stored in the DB (`ADMIN`), the JWT claim (`ADMIN`), and the expression (`hasRole('ADMIN')` → checks `ROLE_ADMIN`). Using `hasAuthority('ADMIN')` keeps all three identical — no hidden translation layer. Documented in `docs/decisions.md`.

**Alternatives considered**:
- `ROLE_ADMIN` everywhere: standard Spring Security convention but requires updating the DB `CHECK` constraint and the JWT role claim — churn with no benefit for a project this size.

---

## Decision 4: SecurityFilterChain Configuration

**Decision**: Declare `SecurityFilterChain` as a `@Bean` in `SecurityConfiguration` annotated with `@Configuration` + `@EnableMethodSecurity`. No `WebSecurityConfigurerAdapter` (removed in Spring Security 6).

**Rationale**: Spring Security 6 (bundled with Spring Boot 3) removed `WebSecurityConfigurerAdapter`. The component-model approach (`@Bean`) is the only supported path.

**Key configuration choices**:
- `SessionCreationPolicy.STATELESS` — no server-side session; every request is self-contained.
- CSRF disabled — REST API with no browser form submissions; CSRF tokens are not meaningful.
- `POST /auth/login` and `GET /health` permitted without authentication; all other paths require authentication.
- `JwtAuthenticationFilter` inserted **before** `UsernamePasswordAuthenticationFilter` so the JWT is resolved before Spring Security's form-login machinery runs.

---

## Decision 5: 401 Response — AuthenticationEntryPoint

**Decision**: Register a custom `AuthenticationEntryPoint` that writes a RFC 7807 Problem Detail JSON body with `status: 401` when authentication fails. Do NOT let Spring Security's default redirect-to-login-form behavior fire.

**Rationale**: The default `LoginUrlAuthenticationEntryPoint` sends a 302 redirect to `/login`, which is wrong for a REST API. A custom entry point returns `401 Unauthorized` with a `application/problem+json` body consistent with the global exception handler.

---

## Decision 6: Token TTL Parsing

**Decision**: `app.security.jwt.access-token-ttl` is bound as a `java.time.Duration` via `@ConfigurationProperties`. Default value `PT12M` (12 minutes). `expiresIn` in the response is `duration.toSeconds()` (e.g. 720).

**Rationale**: `java.time.Duration` parses ISO-8601 duration strings natively and is supported by Spring's `@ConfigurationProperties` binder without custom converters. This keeps the config human-readable (`PT12M`, `PT1H`, `PT30S`) and makes the unit explicit.

---

## Decision 7: Dependency Cleanup — `spring-security-crypto`

**Decision**: Replace the direct `spring-security-crypto` dependency in `pom.xml` with `spring-boot-starter-security`. The `spring-boot-starter-security` transitively includes `spring-security-crypto`, so the explicit dependency becomes redundant.

**Rationale**: `PasswordEncoderConfiguration` was added in phase 2 when only the crypto module was needed. Now the full Spring Security stack is required. Keeping both would compile correctly but is misleading — the starter is the intended entry point.

---

## Decision 8: Seed User Migration

**Decision**: Add Flyway migration `V3__seed_test_users.sql` that inserts two seed users with known bcrypt-hashed passwords so the auth flow can be tested immediately after deployment.

**Rationale**: The `POST /users` endpoint (per README contract) does not accept a `password` field — the README response template is canonical (Constitution IV). Setting `password_hash` requires a direct data operation. A Flyway migration is the cleanest reproducible mechanism in this project. Seed users are clearly marked with a comment noting they are for development/testing.

**Seed credentials** (for `run.md` documentation):
- `admin / Admin1234!` (role: ADMIN)
- `developer / Dev1234!` (role: DEVELOPER)

**BCrypt hashes** (strength 10):
- `Admin1234!` → `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy`
- `Dev1234!`   → `$2a$10$c6bON1nU.GxpwK3HCDKkPO5HGn2Wp1qEqjHKxkWPGwrRtjy1ziqjm`

Note: these hashes should be regenerated with the project's `BCryptPasswordEncoder` at setup time to ensure they match the configured strength. The plan tasks step provides the Maven command.
