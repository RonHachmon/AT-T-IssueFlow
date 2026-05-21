# Phase 1 — Data Model: Users CRUD

## Entity: `User`

JPA `@Entity` mapped to table `users`. Field-to-column naming bridges
camelCase Java to snake_case SQL per constitution Principle IV.

| Field          | Java type   | Column           | Constraints                                       | Mutable via API |
|----------------|-------------|------------------|---------------------------------------------------|------------------|
| `id`           | `Long`      | `id`             | PK, `bigserial` (IDENTITY)                         | no (server-generated) |
| `username`     | `String`    | `username`       | NOT NULL, unique on `LOWER(username)`, 3–50 chars  | no (immutable)        |
| `email`        | `String`    | `email`          | NOT NULL, unique on `LOWER(email)`, well-formed    | no (immutable)        |
| `fullName`     | `String`    | `full_name`      | NOT NULL, trimmed length 1–100                     | yes (via PATCH)       |
| `role`         | `Role`      | `role`           | NOT NULL, `@Enumerated(EnumType.STRING)` → `varchar(20)` CHECK constraint to `{ADMIN, DEVELOPER}` | yes (via PATCH) |
| `passwordHash` | `String`    | `password_hash`  | NULL (no login yet), `varchar(72)` (BCrypt max)    | no API surface yet    |
| `createdAt`    | `Instant`   | `created_at`     | NOT NULL, defaults to `now()`                      | no (server-managed)   |
| `updatedAt`    | `Instant`   | `updated_at`     | NOT NULL, set to `now()` on update                 | no (server-managed)   |

### Java declaration sketch

```java
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "username", nullable = false, unique = true, length = 50)
  private String username;

  @Column(name = "email", nullable = false, unique = true, length = 254)
  private String email;

  @Column(name = "full_name", nullable = false, length = 100)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private Role role;

  @Column(name = "password_hash", length = 72)
  private String passwordHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  // package-private no-arg constructor for JPA; getters/setters via Lombok
}
```

> The `@Column(unique = true)` annotations on `username` and `email` are
> kept for documentation of intent on the field. They do **not** generate
> the constraint (we run with `ddl-auto: none`); the Flyway migration is
> the source of truth and uses functional unique indexes on `LOWER(col)`
> for case-insensitive uniqueness — see [contracts/pagination.md](../001-project-skeleton/contracts/pagination.md)'s sibling section "Case-insensitive uniqueness" in this feature's [research.md](research.md) R2.

## Enum: `Role`

```java
public enum Role {
  ADMIN,
  DEVELOPER
}
```

Stored as `varchar(20)` via `@Enumerated(EnumType.STRING)`. A DB-side
`CHECK` constraint pins the column to those two values so the database
agrees with the Java enum even if the application is bypassed.

## Flyway migration: `V2__users.sql`

```sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    email           VARCHAR(254) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    password_hash   VARCHAR(72),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'DEVELOPER'))
);

CREATE UNIQUE INDEX users_username_lower_unique ON users (LOWER(username));
CREATE UNIQUE INDEX users_email_lower_unique    ON users (LOWER(email));
```

Notes:
- `bigserial` → `id BIGINT` with sequence; aligns with JPA's
  `GenerationType.IDENTITY`.
- `password_hash` is nullable (no login yet).
- Functional unique indexes give case-insensitive uniqueness without
  losing original casing in storage.
- `CHECK` constraint on `role` is belt-and-braces against any caller
  that bypasses JPA.

## Validation rules (declarative on DTOs)

| DTO field (where it appears)              | Annotations                                         | Trigger     |
|-------------------------------------------|-----------------------------------------------------|-------------|
| `CreateUserRequest.username`              | `@NotBlank`, `@Size(min=3, max=50)`, `@Pattern("^[a-zA-Z0-9._-]+$")` | `@Valid` |
| `CreateUserRequest.email`                 | `@NotBlank`, `@Email`, `@Size(max=254)`             | `@Valid`    |
| `CreateUserRequest.fullName`              | `@NotBlank`, `@Size(min=1, max=100)`                | `@Valid`    |
| `CreateUserRequest.role`                  | `@NotNull`                                          | `@Valid`    |
| `UpdateUserRequest.fullName` (optional)   | `@Size(min=1, max=100)` (allows null)               | `@Valid`    |
| `UpdateUserRequest.role` (optional)       | nothing (enum nullability handled in service)       | `@Valid`    |

`@NotBlank` rejects whitespace-only strings (spec FR-007, edge case
"whitespace-only fields"). The role-enum validation in
`UpdateUserRequest` is delegated to Jackson — an unknown enum value
fails deserialization, producing `HttpMessageNotReadableException`
which the existing advice maps to 400.

## DTOs

### `CreateUserRequest`

```java
public record CreateUserRequest(
    @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-zA-Z0-9._-]+$")
    String username,

    @NotBlank @Email @Size(max = 254)
    String email,

    @NotBlank @Size(min = 1, max = 100)
    String fullName,

    @NotNull
    Role role
) {}
```

### `UpdateUserRequest`

```java
public record UpdateUserRequest(
    @Size(min = 1, max = 100)
    String fullName,

    Role role
) {

  /** True when the request has no modifiable field set — used to reject silent no-ops (spec edge case). */
  public boolean isEmpty() {
    return fullName == null && role == null;
  }
}
```

### `UserResponse`

```java
public record UserResponse(
    Long id,
    String username,
    String email,
    String fullName,
    Role role,
    Instant createdAt,
    Instant updatedAt
) {}
```

`passwordHash` is **deliberately absent** from `UserResponse`. MapStruct
will be configured with explicit `@Mapping` for every target field so
that adding a new entity field never auto-leaks into the response.

## State transitions

`User` has no business state machine. The fields that can change:
- `fullName` — any non-blank string within length bounds, set via PATCH.
- `role` — `ADMIN ↔ DEVELOPER`, set via PATCH.

`updatedAt` always advances when any updatable field changes.
`createdAt` is set once on insert and never changes.

## Cross-feature impact

- **First entity to set the identifier convention**: `Long id` becomes
  the project-wide rule. The next entity (Project) MUST follow the
  same strategy. See [research.md](research.md) R1.
- **First entity to write a Flyway migration past the baseline**:
  the V2 migration order convention starts here.
- **First feature to use the canonical `PagedResponse<T>`**: the
  envelope's shape is set here and reused by every future list
  endpoint. See [contracts/pagination.md](contracts/pagination.md).
- **`passwordHash` column lives unused until Phase-2 auth**: documented
  in [research.md](research.md) R3 to make the forward-compatibility
  intent explicit.
