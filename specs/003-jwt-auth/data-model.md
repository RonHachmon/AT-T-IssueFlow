# Data Model: JWT Authentication (003-jwt-auth)

**Branch**: `003-jwt-auth` | **Date**: 2026-05-21

---

## Persistent Entities

### `User` (existing — no schema change)

The `User` entity and `users` table are unchanged from phase 2. The `password_hash` column (`VARCHAR(72)`) already exists. No Flyway migration alters the schema.

| Column          | Type                       | Notes                                  |
|-----------------|----------------------------|----------------------------------------|
| `id`            | `BIGSERIAL` PK             | Auto-generated                         |
| `username`      | `VARCHAR(50)` NOT NULL     | Case-insensitive unique index          |
| `email`         | `VARCHAR(254)` NOT NULL    | Case-insensitive unique index          |
| `full_name`     | `VARCHAR(100)` NOT NULL    |                                        |
| `role`          | `VARCHAR(20)` NOT NULL     | `ADMIN` or `DEVELOPER`                 |
| `password_hash` | `VARCHAR(72)` nullable     | BCrypt hash; populated by V3 seed      |
| `created_at`    | `TIMESTAMPTZ` NOT NULL     |                                        |
| `updated_at`    | `TIMESTAMPTZ` NOT NULL     |                                        |

---

## New Flyway Migration

### `V3__seed_test_users.sql`

Inserts two seed users with pre-hashed passwords for development/testing. Does not alter any column or constraint — purely a data operation.

---

## Transient Objects (never persisted)

### JWT Claim Set

The signed JWT embeds these claims:

| Claim   | Type     | Description                                 |
|---------|----------|---------------------------------------------|
| `sub`   | `String` | Username of the authenticated user          |
| `userId`| `Long`   | Database primary key of the user            |
| `role`  | `String` | Role name — `ADMIN` or `DEVELOPER`          |
| `iat`   | `Long`   | Issued-at timestamp (epoch seconds)         |
| `exp`   | `Long`   | Expiry timestamp (epoch seconds); `iat + TTL` |

---

## DTOs

### `LoginRequest`

| Field      | Type     | Validation           |
|------------|----------|----------------------|
| `username` | `String` | `@NotBlank`          |
| `password` | `String` | `@NotBlank`          |

### `TokenResponse`

| Field         | Type     | Description                                    |
|---------------|----------|------------------------------------------------|
| `accessToken` | `String` | Signed JWT string                              |
| `tokenType`   | `String` | Always `"Bearer"`                              |
| `expiresIn`   | `long`   | Token lifetime in seconds (e.g. `720`)         |

### `CurrentUserResponse`

Reuses `UserResponse` (already defined in the `user` package):

| Field      | Type     |
|------------|----------|
| `id`       | `Long`   |
| `username` | `String` |
| `email`    | `String` |
| `fullName` | `String` |
| `role`     | `String` |

---

## Configuration Properties

### `JwtProperties` (`@ConfigurationProperties(prefix = "app.security.jwt")`)

| Property                    | Type       | Default  | Description                          |
|-----------------------------|------------|----------|--------------------------------------|
| `secret`                    | `String`   | —        | HS256 signing key (required)         |
| `access-token-ttl`          | `Duration` | `PT12M`  | Token lifetime (ISO-8601 duration)   |

Bound in `application.yaml`:

```yaml
app:
  security:
    jwt:
      secret: ${APP_SECURITY_JWT_SECRET:change-me-in-production}
      access-token-ttl: PT12M
```

---

## Security Principal

`IssueFlowUserDetailsService` implements `UserDetailsService` and returns a `UserDetails` (Spring Security `User` record) with:

- `username` = `user.getUsername()`
- `password` = `user.getPasswordHash()` (BCrypt-hashed)
- `authorities` = `[new SimpleGrantedAuthority(user.getRole().name())]` — e.g. `ADMIN`, never `ROLE_ADMIN`
