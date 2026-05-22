# Implementation Plan: Audit Log

**Branch**: `007-audit-log` | **Date**: 2026-05-22 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/007-audit-log/spec.md`

## Summary

Record an immutable entry every time a user creates, updates, or deletes one of the four audited
aggregate roots (`USER`, `PROJECT`, `TICKET`, `COMMENT`). Entries capture `action`, `entityType`,
`entityId`, `performedBy`, `actor` (USER/SYSTEM), `timestamp`, and an internal-only `details` JSONB
column. Expose a single read-only `GET /audit-logs` endpoint restricted to `ADMIN`, with optional
filters `entityType`, `entityId`, `action`, `actor` (combined with AND semantics). The response
shape is a plain JSON array per the canonical README template — explicitly **not** the project's
paginated envelope, accepted by Constitution Principle IV.

Implementation: a JPA `AuditableEntityListener` (`@PostPersist` / `@PostUpdate` / `@PostRemove`)
attached to each audited entity. The listener obtains `AuditLogService` via a static
`SpringContextHolder` bean (JPA entity listeners are not Spring-managed by default). The current
actor is read from `SecurityContextHolder` via a new static `SecurityUtil`. Services that perform
semantically-richer actions (soft delete, restore, status change) set a thread-local
`AuditContext` hint before `save(...)` so the listener emits `SOFT_DELETE` / `RESTORE` /
`STATUS_CHANGE` instead of generic `UPDATE`.

## Technical Context

**Language/Version**: Java 21 on Spring Boot 3.4.2 (per `pom.xml`)
**Primary Dependencies**: Spring Data JPA, Spring Security (JWT via `jjwt` 0.12.6), Hibernate 6.x (transitively, for `@JdbcTypeCode(SqlTypes.JSON)`), Lombok, MapStruct, Bean Validation, Flyway
**Storage**: PostgreSQL in production (Hibernate-native JSONB column for `details`); H2 is unused here because no audit-log test needs a SQL backend
**Testing**: JUnit 5 + Mockito + AssertJ (pure JVM); Postman/Newman for wire-level contract
**Target Platform**: Linux/macOS single-node server
**Project Type**: REST web service (feature-slice packaged)
**Performance Goals**: First page returned in under 300 ms for an entry corpus up to 100k rows (spec SC-003)
**Constraints**: All entries written in the same transaction as the underlying change (FR-003); read endpoint requires `ADMIN`; no write/edit/delete endpoint exists for `/audit-logs` (FR-010); response shape is a plain JSON array per README (deviates from project pagination convention — see Complexity Tracking)
**Scale/Scope**: Same single-tenant scope as existing features; expected entry volume bounded by the rate of write operations on the four audited entities

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| # | Gate | Pass? |
|---|------|-------|
| I  | **Clean Code** — `AuditLogService`, `AuditLogController`, `AuditableEntityListener`, `SecurityUtil`, `SpringContextHolder`, `AuditContext` each have one job and an intent-revealing name. No flag arguments; enums replace magic strings (`AuditAction`, `AuditEntityType`, `ActorKind`); `EntityClassifier` extracts entity → enum mapping into one well-named method. | ✅ |
| II | **Testing Standards** — Pure-JVM JUnit 5 + Mockito tests for: `AuditLogService.record(...)` writes the right fields; `AuditableEntityListener` consumes the thread-local hint on `@PostUpdate`; `SecurityUtil` returns empty when no authentication is present; `AuditLogService.findAll(...)` builds the right query for each filter combination; rejecting `entityId` without `entityType`. Postman collection covers admin list, filters in all combinations, 401 unauthenticated, 403 non-admin, 400 on bad enum / orphan `entityId`, and immutability (405 on PUT/PATCH/DELETE). | ✅ |
| III| **Documentation Discipline** — Javadoc on every new public method (service, controller, listener, repository, mapper, `SecurityUtil`, `SpringContextHolder`, `AuditContext`). README already has the Audit Log row (no update required). `run.md` unchanged (no new startup step). `prompts.md` entry recorded for AI-assisted plan. | ✅ |
| IV | **API Consistency** — Path is plural-noun (`/audit-logs`). Endpoint matches the README canonical template exactly: `200 OK` with a JSON array, fields `id, action, entityType, entityId, performedBy, actor, timestamp`, query params exactly `entityType, entityId, action, actor`. RFC 7807 problem responses for 400/401/403. `camelCase` JSON / `snake_case` SQL. `<entity>Id`-style naming. DTOs at the controller boundary (entity never serialised). MapStruct for entity↔DTO. Bean Validation for query-param coercion (Spring auto-binding handles enum parsing → 400). **Deviation** from the project-wide pagination envelope and from spec FR-005/FR-007 is canonical per README — recorded in Complexity Tracking. | ✅ |

## Project Structure

### Documentation (this feature)

```text
specs/007-audit-log/
├── plan.md                ← this file
├── spec.md
├── research.md            ← Phase 0 decisions
├── data-model.md          ← AuditLog entity + DTO + migration shape
├── quickstart.md          ← developer-facing how-to
├── contracts/
│   └── audit-logs.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md               ← generated by /speckit-tasks (NOT created here)
```

### Source Code (repository root)

```text
src/main/java/com/att/tdp/issueflow/
├── auditlog/                                ← new feature slice
│   ├── AuditLog.java                        ← new @Entity
│   ├── AuditLogController.java              ← GET /audit-logs (ADMIN-only)
│   ├── AuditLogMapper.java                  ← MapStruct entity → DTO
│   ├── AuditLogRepository.java              ← Spring Data JPA + Specification builder
│   ├── AuditLogService.java                 ← write API for the listener; read API for the controller
│   ├── AuditableEntityListener.java         ← @PostPersist / @PostUpdate / @PostRemove
│   ├── AuditAction.java                     ← enum: CREATE, UPDATE, DELETE, SOFT_DELETE, RESTORE, STATUS_CHANGE
│   ├── AuditEntityType.java                 ← enum: USER, PROJECT, TICKET, COMMENT
│   ├── ActorKind.java                       ← enum: USER, SYSTEM
│   ├── AuditContext.java                    ← thread-local action hint set by services
│   ├── EntityClassifier.java                ← maps Object → AuditEntityType + Long id
│   ├── package-info.java                    ← new
│   └── dto/
│       ├── AuditLogResponse.java            ← response shape per README
│       └── AuditLogFilter.java              ← bound query-param object
├── common/security/
│   └── SecurityUtil.java                    ← static currentUsername() / currentAuthority() helpers
├── common/config/
│   └── SpringContextHolder.java             ← ApplicationContextAware; static .bean(Class) lookup
├── user/User.java                           ← add @EntityListeners(AuditableEntityListener.class)
├── project/Project.java                     ← add @EntityListeners(AuditableEntityListener.class)
├── ticket/Ticket.java                       ← add @EntityListeners(AuditableEntityListener.class)
├── comment/Comment.java                     ← extend @EntityListeners with AuditableEntityListener.class
├── project/ProjectService.java              ← hint SOFT_DELETE / RESTORE before save
├── ticket/TicketService.java                ← hint SOFT_DELETE / STATUS_CHANGE before save

src/main/resources/db/migration/
└── V8__audit_logs.sql                       ← new Flyway migration

src/test/java/com/att/tdp/issueflow/
└── auditlog/
    ├── AuditLogServiceTest.java             ← write path + filtered read path
    ├── AuditableEntityListenerTest.java     ← hint consumption + default-to-UPDATE
    ├── SecurityUtilTest.java                ← empty when no auth; username when authenticated
    └── AuditLogControllerAuthorizationTest.java ← 401/403 paths (Mockito + standalone MockMvc)

postman/
└── audit-logs.postman_collection.json        ← Newman-runnable
```

**Structure Decision**: New `auditlog` feature slice mirroring the existing `comment`, `ticket`,
`project`, `user` slices. Two shared utility classes go in `common/security/` (`SecurityUtil`) and
`common/config/` (`SpringContextHolder`) because they are cross-cutting. Four existing entity files
gain a single annotation; two existing services gain one `AuditContext.hint(...)` call each. One
Flyway migration, one Postman collection. The audit-log endpoint itself returns a plain JSON array
per the README canonical template — `PagedResponse` is **not** used.

## Complexity Tracking

| Deviation | Why Accepted | Simpler Alternative Rejected Because |
|-----------|--------------|--------------------------------------|
| Response is a JSON array, not the canonical `PagedResponse` envelope | README "Audit Log APIs" table specifies a plain array for this endpoint. Constitution Principle IV makes the README template authoritative. The project also has other endpoints (Mentions) that use a different envelope shape — diversity here is already accepted by design. | Using `PagedResponse` would silently break the documented contract clients were given and violate Principle IV. |
| Query filters are exactly `entityType, entityId, action, actor` — no `actorId`/`performedBy` filter, contradicting spec FR-007 | README lists exactly these four query params. The user's plan input also enumerates exactly these four. Principle IV: README is canonical. | Adding `performedBy` would create a fifth, undocumented query param. The spec will be amended at next clarification round to match the canonical contract. |
| No pagination, contradicting spec FR-005 | README does not specify pagination for this endpoint. Spec FR-005 was drafted before consulting the README and assumed the project-wide pagination convention. | Same as above. Future scaling concern is mitigated by `timestamp DESC` index and ADMIN-only access (low query volume). |
| JPA entity listener accesses Spring beans via a static `SpringContextHolder` rather than Spring DI | JPA instantiates entity listeners directly via reflection; there is no clean built-in DI for them in Spring Boot 3.x. The user explicitly approved a "static helper" pattern in the plan input. | Alternatives — `SpringBeanAutowiringInterceptor` (deprecated), `@Configurable` + AspectJ weaving (heavy-weight, adds a build step), or hand-rolled wiring in `AuditingEntityListener` style — all add more complexity than a 15-line `ApplicationContextAware` holder. |
| Thread-local `AuditContext` action hint set by services before `repository.save(...)` | `@PostUpdate` cannot intrinsically distinguish `UPDATE` from `SOFT_DELETE` / `RESTORE` / `STATUS_CHANGE` — only the calling service knows the semantic intent. A thread-local hint is the lightest-touch way to communicate it without changing repository signatures or coupling the listener to service code. | Subclassing `PostUpdateEventListener` to inspect Hibernate's `oldState` array is fragile (state slot indices depend on entity field ordering). Adding a dedicated `softDeleteAndAudit(...)` method on every repository would couple persistence to audit concerns and break our service-as-orchestrator pattern. |
| `details` JSONB column added now but populated nowhere in v1 | User explicitly directed it; field is forward-compatible for later phases that will capture old/new value diffs. Cost is one column and one Hibernate mapping; benefit is no breaking schema change later. | Omitting the column would force a future Flyway migration that adds a JSONB column on a large table — non-trivial. |

## Implementation Notes

### `AuditLog` Entity

```java
@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor
public class AuditLog {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, updatable = false, length = 32)
  private AuditAction action;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, updatable = false, length = 32)
  private AuditEntityType entityType;

  @Column(name = "entity_id", nullable = false, updatable = false)
  private Long entityId;

  @Column(name = "performed_by", updatable = false)
  private Long performedBy;     // nullable when actor = SYSTEM

  @Enumerated(EnumType.STRING)
  @Column(name = "actor", nullable = false, updatable = false, length = 16)
  private ActorKind actor;

  @Column(name = "timestamp", nullable = false, updatable = false)
  private Instant timestamp;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "details", columnDefinition = "jsonb")
  private Map<String, Object> details;   // internal; not exposed in API response
}
```

- `updatable = false` on every audit column is the entity-level enforcement of FR-013 (entries are
  immutable). Combined with the absence of any controller write surface (FR-010), this makes
  modification impossible through normal application code paths.

### `AuditableEntityListener`

```java
public class AuditableEntityListener {

  @PostPersist
  public void onCreate(Object entity) {
    SpringContextHolder.bean(AuditLogService.class)
        .record(AuditContext.consumeOrDefault(AuditAction.CREATE), entity);
  }

  @PostUpdate
  public void onUpdate(Object entity) {
    SpringContextHolder.bean(AuditLogService.class)
        .record(AuditContext.consumeOrDefault(AuditAction.UPDATE), entity);
  }

  @PostRemove
  public void onRemove(Object entity) {
    SpringContextHolder.bean(AuditLogService.class)
        .record(AuditContext.consumeOrDefault(AuditAction.DELETE), entity);
  }
}
```

- The listener is attached via `@EntityListeners(AuditableEntityListener.class)` on `User`,
  `Project`, `Ticket`, and `Comment`. `Comment` already declares
  `@EntityListeners(AuditingEntityListener.class)` — extend its array, do not replace.
- `Bean lookup` happens at callback time, not construction time, so it works even though JPA
  bootstraps before the Spring context is fully ready.

### `AuditContext`

```java
public final class AuditContext {
  private static final ThreadLocal<AuditAction> HINT = new ThreadLocal<>();

  public static void hint(AuditAction action) { HINT.set(action); }

  public static AuditAction consumeOrDefault(AuditAction fallback) {
    AuditAction hint = HINT.get();
    HINT.remove();
    return hint != null ? hint : fallback;
  }

  private AuditContext() {}
}
```

- Service callers do `AuditContext.hint(AuditAction.SOFT_DELETE)` immediately before
  `repository.save(...)`. The listener's `@PostUpdate` consumes the hint and clears it.
- One-shot semantics avoid hint-leak across requests (the call to `consumeOrDefault` always
  removes the value).

### `SecurityUtil`

```java
public final class SecurityUtil {

  public static Optional<String> currentUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return Optional.empty();
    }
    Object principal = auth.getPrincipal();
    if (principal instanceof UserDetails ud) return Optional.of(ud.getUsername());
    if (principal instanceof String s)        return Optional.of(s);
    return Optional.empty();
  }

  private SecurityUtil() {}
}
```

- Used by `AuditLogService.record(...)` to resolve the actor. If `currentUsername()` is empty,
  the entry is written with `performedBy = null` and `actor = SYSTEM`.

### `AuditLogService` — public surface

```java
public interface AuditLogService {
  void record(AuditAction action, Object entity);   // called only by the listener
  List<AuditLogResponse> findAll(AuditLogFilter filter);
}
```

- `record(...)` classifies the entity via `EntityClassifier`, resolves the actor via
  `SecurityUtil` + `UserRepository.findByUsernameIgnoreCase`, and saves a new `AuditLog`. It is
  the only path that writes to `audit_logs`.
- `findAll(...)` builds a JPA `Specification` from `AuditLogFilter` (any subset of the four
  fields) and returns matches sorted by `timestamp DESC`. The result is mapped to
  `List<AuditLogResponse>` for the controller.

### `AuditLogFilter` validation

```java
public record AuditLogFilter(
    AuditEntityType entityType,
    Long entityId,
    AuditAction action,
    ActorKind actor) {

  public void validate() {
    if (entityId != null && entityType == null) {
      throw new InvalidFilterException("entityId requires entityType to be specified");
    }
  }
}
```

- `InvalidFilterException` is a new exception mapped to `400 Bad Request` via
  `GlobalExceptionHandler`. Spring's built-in enum binding handles unknown `entityType` / `action`
  / `actor` values by raising `MethodArgumentTypeMismatchException`, which we add a handler for
  (also `400`).

### `AuditLogController`

```java
@RestController
@RequestMapping("/audit-logs")
public class AuditLogController {

  private final AuditLogService auditLogService;

  @GetMapping
  @PreAuthorize("hasAuthority('ADMIN')")
  @ResponseStatus(HttpStatus.OK)
  public List<AuditLogResponse> list(
      @RequestParam(required = false) AuditEntityType entityType,
      @RequestParam(required = false) Long entityId,
      @RequestParam(required = false) AuditAction action,
      @RequestParam(required = false) ActorKind actor) {
    AuditLogFilter filter = new AuditLogFilter(entityType, entityId, action, actor);
    filter.validate();
    return auditLogService.findAll(filter);
  }
}
```

- Only one method, only `GET`. No `@PostMapping`, `@PutMapping`, `@PatchMapping`, or
  `@DeleteMapping` exists on this resource — the absence is the enforcement of FR-010. Spring will
  return `405 Method Not Allowed` for any other verb (`HiddenHttpMethodFilter` is not in play).

### Flyway migration `V8__audit_logs.sql`

```sql
CREATE TABLE audit_logs (
    id            BIGSERIAL    PRIMARY KEY,
    action        VARCHAR(32)  NOT NULL,
    entity_type   VARCHAR(32)  NOT NULL,
    entity_id     BIGINT       NOT NULL,
    performed_by  BIGINT       NULL,
    actor         VARCHAR(16)  NOT NULL,
    timestamp     TIMESTAMPTZ  NOT NULL,
    details       JSONB        NULL
);

CREATE INDEX idx_audit_logs_timestamp_desc ON audit_logs (timestamp DESC);
CREATE INDEX idx_audit_logs_entity_type    ON audit_logs (entity_type);
CREATE INDEX idx_audit_logs_entity         ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_logs_performed_by   ON audit_logs (performed_by);
CREATE INDEX idx_audit_logs_action         ON audit_logs (action);
```

- No FK on `performed_by` or `entity_id` — FR-011 requires entries to survive deletion of the
  referenced row, so referential integrity at the DB layer would be a defect, not a feature.
- `timestamp DESC` index supports the default ordering of the list endpoint.

### Service-layer hints

| Existing operation | Hint inserted before `repository.save(...)` |
|---|---|
| `ProjectService.softDelete(...)` | `AuditContext.hint(AuditAction.SOFT_DELETE)` |
| `ProjectService.restore(...)` *(not yet implemented; will be added by Soft Delete feature)* | `AuditContext.hint(AuditAction.RESTORE)` |
| `TicketService.softDelete(...)` | `AuditContext.hint(AuditAction.SOFT_DELETE)` |
| `TicketService.restore(...)` *(not yet implemented)* | `AuditContext.hint(AuditAction.RESTORE)` |
| `TicketService.update(...)` when `request.status() != null` | `AuditContext.hint(AuditAction.STATUS_CHANGE)` *(takes precedence over generic UPDATE for a status-bearing update)* |

- All other writes (User create, User update, Project create, Project update, Ticket create,
  generic Ticket update, Comment create/update/delete) get the default action from the listener
  (`CREATE` / `UPDATE` / `DELETE`).

### Unit tests (`AuditLogServiceTest`, `AuditableEntityListenerTest`, `SecurityUtilTest`)

| Test | Scenario |
|------|----------|
| `recordsCreateAuditEntryWithAuthenticatedActor` | `SecurityContextHolder` has a known username → `performedBy` resolved via `UserRepository`, `actor = USER` |
| `recordsCreateAuditEntryWithSystemActorWhenNoAuthentication` | No `Authentication` in context → `performedBy = null`, `actor = SYSTEM` |
| `findAllReturnsEntriesSortedByTimestampDescending` | Repository returns mixed entries; service returns them newest-first |
| `findAllAppliesEntityTypeFilter` | Filter on `TICKET` → specification rejects non-ticket rows |
| `findAllAppliesAllFourFiltersCombinedWithAnd` | All four filters supplied → specification yields the AND-intersection |
| `validateThrowsWhenEntityIdSuppliedWithoutEntityType` | `entityId=5, entityType=null` → `InvalidFilterException` |
| `listenerEmitsHintedActionInsteadOfDefault` | `AuditContext.hint(SOFT_DELETE)` before invoking `@PostUpdate` → `record` called with `SOFT_DELETE` |
| `listenerFallsBackToUpdateWhenNoHint` | No hint set → `record` called with `UPDATE` |
| `listenerClearsHintAfterConsumption` | After one consume, next `@PostUpdate` reverts to default |
| `currentUsernameReturnsEmptyForAnonymousAndNullAuth` | `SecurityUtil` two negative cases |
| `currentUsernameReturnsUsernameFromUserDetailsPrincipal` | Spring `UserDetails` principal happy path |

`AuditLogControllerAuthorizationTest` is a standalone-MockMvc test (no full Spring context) that
verifies the `@PreAuthorize` is enforced — 401 for unauthenticated, 403 for `DEVELOPER`,
200 for `ADMIN`.

### Postman collection (`audit-logs.postman_collection.json`)

Setup:
- Re-use the existing `authToken` environment variable.
- Add an `adminAuthToken` variable populated by an admin login pre-request.

Cases:
- `GET /audit-logs` as admin → `200 OK` with array
- `GET /audit-logs` as developer → `403 Forbidden`
- `GET /audit-logs` unauthenticated → `401 Unauthorized`
- `GET /audit-logs?entityType=TICKET` → only ticket entries returned
- `GET /audit-logs?entityType=TICKET&entityId=5&action=UPDATE&actor=USER` → all four filters applied
- `GET /audit-logs?entityId=5` → `400 Bad Request` (orphan `entityId`)
- `GET /audit-logs?entityType=BANANA` → `400 Bad Request` (unknown enum value)
- `POST /audit-logs` as admin → `405 Method Not Allowed`
- `DELETE /audit-logs/1` as admin → `405` or `404` (no route defined)

Smoke flow to prove end-to-end recording:
1. Admin login.
2. Admin `POST /projects` (creates a project).
3. Admin `GET /audit-logs?entityType=PROJECT&action=CREATE` → at least one entry with
   `performedBy` = admin id, `actor = USER`, `timestamp` within the last few seconds.

### README updates

The README already contains the Audit Log API row (matching the canonical template that this plan
implements). **No README change is required for this feature.**

### `run.md` updates

No changes — no new environment variables, no new ports, no new startup steps.

### `prompts.md` entry

Add one entry summarising the AI-assisted /speckit-specify → /speckit-plan cycle for the audit
log feature, naming the model and quoting the key planning prompt.
