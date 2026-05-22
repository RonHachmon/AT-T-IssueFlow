# Phase 1 — Data Model

## Entity: `AuditLog`

| Field | Java type | Column | Nullable | Updatable | Notes |
|---|---|---|---|---|---|
| `id` | `Long` | `id BIGSERIAL PK` | no | n/a (PK) | Database-generated identity |
| `action` | `AuditAction` enum | `action VARCHAR(32)` | no | **no** | Stored as string |
| `entityType` | `AuditEntityType` enum | `entity_type VARCHAR(32)` | no | **no** | Stored as string |
| `entityId` | `Long` | `entity_id BIGINT` | no | **no** | Plain numeric reference, no FK |
| `performedBy` | `Long` | `performed_by BIGINT` | **yes** | **no** | Plain numeric reference, no FK; nullable when `actor = SYSTEM` |
| `actor` | `ActorKind` enum | `actor VARCHAR(16)` | no | **no** | `USER` or `SYSTEM` |
| `timestamp` | `Instant` | `timestamp TIMESTAMPTZ` | no | **no** | Set by `AuditLogService.record(...)` at write time |
| `details` | `Map<String, Object>` | `details JSONB` | yes | yes | Internal-only; not exposed in API; `null` in v1 |

**All audit columns except `id` and `details` are marked `updatable = false`** at the JPA layer.
Combined with the absence of any write controller (no `POST` / `PUT` / `PATCH` / `DELETE`
mapping on `/audit-logs`), this enforces FR-010 and FR-013 — the record is immutable through any
normal application code path.

---

## Enums

### `AuditAction`

| Value | When emitted | Phase |
|---|---|---|
| `CREATE` | `@PostPersist` fires with no hint | v1 |
| `UPDATE` | `@PostUpdate` fires with no hint | v1 |
| `DELETE` | `@PostRemove` fires (hard delete) | v1 |
| `SOFT_DELETE` | Service hints before `save(...)` that sets `deletedAt` | v1 |
| `RESTORE` | Service hints before `save(...)` that clears `deletedAt` | v1 (only used after the Soft-Delete Restore endpoints are implemented) |
| `STATUS_CHANGE` | `TicketService.update(...)` hints when `request.status() != null` | v1 |
| `AUTO_ASSIGN` | Reserved — auto-assigner emits with `actor = SYSTEM` | later phase |
| `AUTO_ESCALATE` | Reserved — auto-escalator emits with `actor = SYSTEM` | later phase |

The reserved later-phase values are present in the Java enum (and therefore the schema-allowed
strings) from day one so no future code change has to widen the enum once the auto-assigner
ships.

### `AuditEntityType`

| Value | Maps to entity class |
|---|---|
| `USER` | `com.att.tdp.issueflow.user.User` |
| `PROJECT` | `com.att.tdp.issueflow.project.Project` |
| `TICKET` | `com.att.tdp.issueflow.ticket.Ticket` |
| `COMMENT` | `com.att.tdp.issueflow.comment.Comment` |

`EntityClassifier` is the only place this mapping lives — a single `switch` on `instanceof` that
returns the enum value and the entity's `getId()`. Adding a new audited entity is two lines: a new
enum value and a new `case`.

### `ActorKind`

| Value | Meaning |
|---|---|
| `USER` | `performedBy` resolved from `SecurityContextHolder` to a known user id |
| `SYSTEM` | `performedBy` is `null` (no authentication available; only used in later phases by background jobs) |

---

## DTO: `AuditLogResponse`

Shape — matches the README canonical template exactly:

```json
{
  "id": 1,
  "action": "CREATE",
  "entityType": "TICKET",
  "entityId": 5,
  "performedBy": 2,
  "actor": "USER",
  "timestamp": "2026-03-01T10:00:00Z"
}
```

| JSON field | Java type | Source |
|---|---|---|
| `id` | `Long` | `AuditLog.id` |
| `action` | `String` (enum name) | `AuditLog.action` |
| `entityType` | `String` (enum name) | `AuditLog.entityType` |
| `entityId` | `Long` | `AuditLog.entityId` |
| `performedBy` | `Long` or `null` | `AuditLog.performedBy` |
| `actor` | `String` (enum name) | `AuditLog.actor` |
| `timestamp` | ISO-8601 `Instant` | `AuditLog.timestamp` |

`details` is **not** in the response. `MapStruct` mapper unmaps it explicitly to satisfy
`unmappedTargetPolicy=ERROR`.

The list endpoint returns `List<AuditLogResponse>` — JSON array per the README template — and
**not** the `PagedResponse` envelope (see plan Complexity Tracking).

---

## Filter object: `AuditLogFilter`

```java
public record AuditLogFilter(
    AuditEntityType entityType,
    Long entityId,
    AuditAction action,
    ActorKind actor) { ... }
```

Validation rule:
- `entityId != null && entityType == null` → throw `InvalidFilterException` → `400 Bad Request`.

All other combinations (including all-null = "no filter") are valid. Enum query params are coerced
by Spring's built-in binding; unknown values raise `MethodArgumentTypeMismatchException`, mapped
to `400 Bad Request` by a new handler in `GlobalExceptionHandler`.

---

## Migration `V8__audit_logs.sql`

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

No `CHECK` constraints on the enum columns — Hibernate already constrains writes to enum names,
and we want manual SQL inserts (test data, fixtures) to remain ergonomic.

No `REFERENCES` clauses — see research.md Decision 6. The lack of FK on `performed_by` and
`entity_id` is intentional: FR-011 requires entries to survive the deletion of the referenced
row.

---

## Effect on existing entities

| File | Change |
|---|---|
| [src/main/java/com/att/tdp/issueflow/user/User.java](../../src/main/java/com/att/tdp/issueflow/user/User.java) | Add `@EntityListeners(AuditableEntityListener.class)` |
| [src/main/java/com/att/tdp/issueflow/project/Project.java](../../src/main/java/com/att/tdp/issueflow/project/Project.java) | Add `@EntityListeners(AuditableEntityListener.class)` |
| [src/main/java/com/att/tdp/issueflow/ticket/Ticket.java](../../src/main/java/com/att/tdp/issueflow/ticket/Ticket.java) | Add `@EntityListeners(AuditableEntityListener.class)` |
| [src/main/java/com/att/tdp/issueflow/comment/Comment.java](../../src/main/java/com/att/tdp/issueflow/comment/Comment.java) | Extend existing `@EntityListeners(AuditingEntityListener.class)` to `@EntityListeners({AuditingEntityListener.class, AuditableEntityListener.class})` |

No schema changes to existing tables. No DTO changes to existing endpoints.
