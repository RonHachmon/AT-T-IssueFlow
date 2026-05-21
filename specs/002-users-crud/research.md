# Phase 0 — Research: Users CRUD

No open `[NEEDS CLARIFICATION]` markers. The items below capture the
deliberate technical decisions and rejected alternatives.

---

## R1. Identifier strategy: `Long` (auto-increment) vs `UUID`

- **Decision**: `Long` primary keys, `@GeneratedValue(strategy =
  GenerationType.IDENTITY)`. **All future entities (`Project`, `Ticket`,
  `Comment`, etc.) MUST use the same strategy** — consistency was the
  user's explicit instruction.
- **Rationale**:
  - Simpler URLs (`/users/42` vs `/users/3fa85f64-...`), simpler logs,
    smaller indexes, faster joins.
  - Single-region deployment with one Postgres instance — distributed-id
    collisions are not a concern.
  - User explicitly recommended `Long`.
- **Alternatives considered**:
  - **`UUID`**: rejected — gain (no cross-region collision) doesn't
    apply to current deployment topology, cost (bigger indexes, uglier
    URLs) does.
  - **String slug**: rejected — would require additional uniqueness
    rules and would couple to a chosen "natural key" (username), which
    is already the immutable business key but is too long for an
    identifier.

---

## R2. Case-insensitive uniqueness on `username` and `email`

- **Decision**:
  - **Database**: a functional unique index on `LOWER(username)` and
    `LOWER(email)` in `V2__users.sql`. Plain (case-sensitive) unique
    constraints are NOT used.
  - **Application**: `UserRepository.existsByUsernameIgnoreCase(...)` /
    `existsByEmailIgnoreCase(...)` derived-query methods called from
    `UserService.create(...)` BEFORE `save(...)`. The DB index is the
    safety net; the app-level check is the friendly path that produces
    a 409 ProblemDetail naming the offending field.
- **Rationale**:
  - Spec FR-003 / FR-004 require case-insensitive uniqueness. Storing
    in original casing (spec assumption) rules out normalizing to
    lowercase on insert.
  - Postgres unique constraints are case-sensitive by default. A
    functional unique index on `LOWER(col)` is the standard idiom for
    case-insensitive uniqueness without using CITEXT.
  - The app-level pre-check is what surfaces "username already taken"
    as a 409. Without it, the DB throws `DataIntegrityViolationException`
    on `save()`, which is a 500 by default — bad UX.
  - The DB index covers the race window between the pre-check and the
    save. The service catches `DataIntegrityViolationException` and
    maps it to the same 409.
- **Alternatives considered**:
  - **Postgres `CITEXT`** type: works but requires the `citext`
    extension installed in the database — extra deployment step, no
    clear win over functional indexes.
  - **Normalize-to-lowercase on insert**: rejected — spec says preserve
    original casing.
  - **App-level check only**: rejected — fails under race conditions,
    and the spec's "no duplicate user ever ends up persisted" SC-002 is
    a 100% guarantee that requires DB-level enforcement.

> **Note on `@Column(unique = true)`**: The user-supplied plan input asked
> for the JPA annotation on `username` / `email`. With `ddl-auto: none`
> (per skeleton), the annotation does NOT create a constraint — Flyway
> owns the schema. We keep the annotation as documentation of intent on
> the entity field, but it is the Flyway functional index that actually
> enforces uniqueness.

---

## R3. Password storage: column added now, no API surface yet

- **Decision**:
  - `users.password_hash` column added in `V2__users.sql`, **nullable**.
  - `BCryptPasswordEncoder` registered as a `@Bean` in
    `common/security/PasswordEncoderConfiguration.java`.
  - `CreateUserRequest` and `UpdateUserRequest` do NOT accept a
    password field in this feature.
  - `UserResponse` MUST NOT include `passwordHash` (validated by
    MapStruct's explicit field mapping).
- **Rationale**:
  - User input: "add a passwordHash column now (even though login comes
    in Phase 2) so you don't migrate the table later" — forward
    compatibility with the auth feature.
  - Spec: "No login yet — this phase just establishes who exists in the
    system." Accepting a password now would be scope creep.
  - The encoder bean is in place so the auth feature can wire it in
    without a configuration change.
- **Alternatives considered**:
  - **Defer the column to the auth feature**: rejected — one schema
    migration touching an existing populated table is more disruptive
    than adding the column on day one.
  - **Make the column NOT NULL with a placeholder**: rejected —
    introduces a magic value (e.g. `""` or a fake hash) that future
    code might misinterpret as a real credential. Nullable + explicit
    "must be set before login is allowed" is cleaner.
  - **Accept an optional `password` field on `CreateUserRequest`**:
    rejected for this feature — spec says no login, and accepting a
    password creates a securable surface that has no securing yet.

---

## R4. Entity ↔ DTO mapping: MapStruct vs hand-rolled vs ModelMapper

- **Decision**: MapStruct (`org.mapstruct:mapstruct` + annotation
  processor) at compile time. `UserMapper` is a `@Mapper(componentModel
  = "spring")` interface; the Spring-component-style component model
  registers the generated implementation as a bean.
- **Rationale**:
  - User input mandated MapStruct.
  - Compile-time generation = no reflection at runtime, no surprises
    at startup, errors land at `mvn compile`.
  - Generated code is human-readable (in `target/generated-sources`),
    so a reviewer can audit what's happening.
- **Alternatives considered**:
  - **Hand-rolled mapping**: rejected by constitution Principle IV
    ("popular libraries over hand-rolled").
  - **ModelMapper**: runtime reflection, slower, more surprises.
  - **Lombok `@Mapping`**: not a real thing — Lombok doesn't do
    mappers.
- **Annotation processor ordering**: MapStruct's processor must run
  AFTER Lombok's in `pom.xml` so it sees the Lombok-generated getters.
  The existing `maven-compiler-plugin` block needs a second entry
  added for `mapstruct-processor` AFTER `lombok`. Order matters.
- **Lombok–MapStruct binding**: MapStruct 1.5+ requires the
  `lombok-mapstruct-binding` annotation processor when both are used,
  to teach MapStruct about Lombok's generated accessors. Without it,
  MapStruct generates code that uses `record`-style component
  accessors and breaks on Lombok-annotated classes.

---

## R5. Pagination envelope shape (project-wide canonical contract)

- **Decision**: A single `PagedResponse<T>` record in
  `common/pagination/`:

  ```java
  public record PagedResponse<T>(List<T> data, int page, int pageSize, long total) {}
  ```

  Field meanings:
  - `data` — the rows on this page.
  - `page` — 0-indexed page number (matches Spring Data's `Pageable`).
  - `pageSize` — the requested or default page size.
  - `total` — total count across all pages (`long` because counts can
    exceed `Integer.MAX_VALUE`).
- **Rationale**:
  - Constitution Principle IV requires one canonical pagination shape
    across all endpoints. This is the first feature to need it, so we
    establish it here and every later list endpoint reuses it.
  - Spring Data's native `Page<T>` JSON shape is verbose and exposes
    Spring-Data-isms (`pageable.sort.sorted`, `last`, `first`, etc.)
    that we do not want to commit to as a public contract.
- **Alternatives considered**:
  - **Spring Data's native `Page<T>`**: rejected — verbose, leaks
    framework internals, harder to keep stable across Spring upgrades.
  - **Cursor pagination (`{ data, nextCursor }`)**: rejected for now
    — offset pagination is what the spec implies (`page=N`), and the
    expected dataset size doesn't motivate cursors.

---

## R6. Test layering for this feature

- **Decision**:
  - `UserServiceTest` — pure JVM, JUnit 5, Mockito. Mocks
    `UserRepository`, `UserMapper`, `BCryptPasswordEncoder` (latter to
    confirm it is NOT invoked in this phase). Covers:
    - happy-path create
    - duplicate-username rejection (pre-check)
    - duplicate-email rejection (pre-check)
    - duplicate caught at save time (race-condition path,
      `DataIntegrityViolationException` → `DuplicateResourceException`)
    - getById returns mapped DTO
    - getById throws `NotFoundException` for unknown id
    - list returns canonical `PagedResponse`
    - update applies only present fields (partial update)
    - update rejects username/email change (immutability)
    - update throws `NotFoundException` for unknown id
    - delete removes record
    - delete throws `NotFoundException` for unknown id
  - `UserControllerTest` — pure JVM, JUnit 5, Mockito. Mocks
    `UserService`. Covers:
    - create returns 201 with `Location` header
    - getById returns 200 with body
    - list returns 200 with PagedResponse
    - update returns 204 (no body)
    - delete returns 204 (no body)
  - Postman collection: every endpoint plus 422 (bad email), 409
    (duplicate username), 404 (unknown id) cases.
- **Rationale**:
  - Constitution Principle II makes the service tests mandatory
    (branching: duplicate, immutability, not-found). Controller tests
    are mandatory because each endpoint has a different status code
    contract (201/200/204) — that's branching too.
  - No `@WebMvcTest` planned: validation-trigger behavior
    (`@Valid` → `MethodArgumentNotValidException` → 422) is covered
    end-to-end by Postman, which is more honest than re-asserting
    Spring's behavior with a slow Spring context test.
- **Alternatives considered**:
  - **`@WebMvcTest(UserController.class)`** for validation tests:
    deferred — adds Spring context to the test suite for behavior
    Bean Validation already guarantees. Postman covers the wire
    contract; re-testing it in JVM adds cost without proportional
    signal.
  - **Service tests via `@DataJpaTest`** with H2: rejected — service
    layer's job is orchestration, not persistence. Mocking the
    repository keeps the test focused.

---

## R7. Repository query methods

- **Decision**: Spring Data JPA derived-query method names. No custom
  `@Query` SQL in this feature.

  ```java
  boolean existsByUsernameIgnoreCase(String username);
  boolean existsByEmailIgnoreCase(String email);
  // findById / save / deleteById / findAll(Pageable) inherited
  ```
- **Rationale**:
  - Constitution Principle IV: popular libraries over hand-rolled.
    Spring Data's derived queries are popular and zero-maintenance
    for this complexity.
  - The two `IgnoreCase` methods translate (with Postgres) into
    `WHERE LOWER(username) = LOWER(?)`, which the functional unique
    index on `LOWER(username)` covers — duplicate checks stay
    index-backed.
- **Alternatives considered**:
  - **Custom `@Query` with `LOWER(...)`**: equivalent, but more code
    to maintain.
  - **JPA Criteria API**: overkill for a single predicate.

---

## R8. Where the BCrypt encoder bean lives

- **Decision**: `common/security/PasswordEncoderConfiguration.java`
  — a one-method `@Configuration` class that produces a single
  `PasswordEncoder` bean instantiated as `new BCryptPasswordEncoder()`.
  Return type `PasswordEncoder` (the interface), not the concrete
  class, so the auth feature can swap implementations (Argon2, etc.)
  without changing every callsite.
- **Rationale**:
  - `spring-security-crypto` provides `BCryptPasswordEncoder` without
    pulling in the rest of Spring Security. The bean is unused in
    this feature; defining it now means Phase-2 auth wires it up by
    `@Autowired PasswordEncoder` and nothing else.
  - Returning the interface is standard Spring style for swappable
    strategies.
- **Alternatives considered**:
  - **`@Bean` inline in `IssueFlowApplication`**: works but clutters
    the main class.
  - **No bean now, add it in Phase 2**: defeats the user's
    forward-compatibility goal.

---

## R9. Exception model: distinct domain exceptions vs catch-everywhere

- **Decision**: Two new lightweight `RuntimeException` subclasses in
  `common/error/`:
  - `NotFoundException(String resource, Long id)` — thrown by service
    methods for unknown IDs. Mapped to 404 + `ErrorType.NOT_FOUND`
    via a new `@ExceptionHandler` on `GlobalExceptionHandler`.
  - `DuplicateResourceException(String field, String value)` — thrown
    by `UserService.create()` when a pre-check or
    `DataIntegrityViolationException` reveals a duplicate. Mapped to
    409 + a new `ErrorType.DUPLICATE_RESOURCE` constant.
- **Rationale**:
  - Service code throws semantic exceptions; HTTP-status mapping
    lives in the advice (constitution Principle IV: "One advice, one
    place").
  - Both exceptions carry structured fields (`field`, `value` or
    `resource`, `id`) so the handler can build a `ProblemDetail`
    whose `detail` message names the offending value.
- **Alternatives considered**:
  - **Throw `ResponseStatusException`**: works but couples the
    service to HTTP semantics — bad layering.
  - **Reuse Spring Data's `EmptyResultDataAccessException`** for
    not-found: too implementation-specific. A domain exception is
    cleaner.

---

## R10. README endpoint table cleanup

- **Decision**: Replace the existing Users APIs section in
  `README.md` to reflect the constitutional verbs/status codes:
  - `POST /users` → `201 Created` (not `200 OK` as currently
    documented)
  - `PATCH /users/{userId}` → `204 No Content` (not
    `POST /users/update/:userId 200 OK`)
  - `DELETE /users/{userId}` → `204 No Content` (not `200 OK`)
  - Add 4 new request/response fields: `createdAt`, `updatedAt`,
    pagination envelope keys.
- **Rationale**:
  - The current README rows violate constitution Principle IV. The
    PR that introduces the actual endpoints must also align the
    documentation.
- **Alternatives considered**:
  - **Leave the old rows alone**: rejected — would leave the README
    documenting a non-existent contract.

---

## Open Questions / NEEDS CLARIFICATION

None.
