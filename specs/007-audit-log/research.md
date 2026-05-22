# Phase 0 — Research & Decisions

All decisions below resolve potential `NEEDS CLARIFICATION` items raised against the
Technical Context in `plan.md`. There are no remaining open questions blocking Phase 1.

---

### Decision 1 — Mechanism for capturing entity changes: JPA entity listeners

**Decision**: Use `@EntityListeners(AuditableEntityListener.class)` with JPA lifecycle callbacks
(`@PostPersist`, `@PostUpdate`, `@PostRemove`) on each audited entity (`User`, `Project`,
`Ticket`, `Comment`).

**Rationale**:
- Listeners run *inside* the same Hibernate Session and transaction as the change itself.
  An audit-entry insert added in the listener participates in the same transactional unit, so
  rollback of the entity change rolls back the audit entry (FR-003).
- Listeners cannot be bypassed by anyone holding a repository reference — every call route ends
  up flushing through Hibernate, which triggers callbacks. AOP wrapped around a hypothetical
  `@Auditable` annotation can be bypassed by direct `EntityManager` use or by repository methods
  that do not match the pointcut.
- The mechanism is already proven in this codebase via Spring Data's `AuditingEntityListener`
  on `Comment`, so reviewers know the pattern.

**Alternatives considered**:
- **Spring AOP with `@Auditable` advice on service methods.** Rejected — bypassable by direct
  repository calls and any future test/utility code; runs *outside* the transactional boundary
  if misconfigured; couples audit logic to method invocation rather than to data change.
- **Hibernate Envers.** Rejected — overkill for the metadata-only payload this phase needs;
  introduces an unwanted second persistence concern (revision tables) and a transitive
  dependency; produces a different schema than the user-defined one.
- **Domain-event publication from services.** Rejected — relies on every service author to
  remember to publish; the listener guarantees coverage without authorial discipline.

---

### Decision 2 — DI inside a JPA entity listener: static `SpringContextHolder`

**Decision**: Add a `SpringContextHolder` bean implementing `ApplicationContextAware` that
exposes `public static <T> T bean(Class<T> type)`. `AuditableEntityListener` calls
`SpringContextHolder.bean(AuditLogService.class)` inside each lifecycle callback.

**Rationale**:
- JPA instantiates entity listeners directly via reflection. There is no clean built-in DI hook
  for them in Spring Boot 3.x.
- `SpringContextHolder` is ~15 lines, has a single responsibility, and is a well-known Spring
  pattern. The static lookup happens at callback time (not at listener construction), so it
  works even before the full Spring context is available during early bootstrap.
- The user's plan input explicitly approved a static-helper pattern for `SecurityUtil`. Reusing
  the same shape for `AuditLogService` lookup keeps the codebase consistent.

**Alternatives considered**:
- **`SpringBeanAutowiringInterceptor`.** Rejected — deprecated in Spring 6 and removed from
  newer Boot starters' transitive dependencies.
- **`@Configurable` + AspectJ load-time weaving.** Rejected — requires the `aspectjweaver`
  java-agent at runtime *and* a Maven plugin at build time. Heavy for a two-callback need.
- **`HibernatePropertiesCustomizer` to register a custom `EntityManagerFactory` with DI for
  listeners.** Rejected — too clever for this codebase; obscure to a new reader.

---

### Decision 3 — Distinguishing semantic actions on `@PostUpdate`: thread-local `AuditContext`

**Decision**: A new `AuditContext` utility holds a `ThreadLocal<AuditAction>` hint. Services that
perform semantically-richer updates (soft delete, restore, status change) call
`AuditContext.hint(AuditAction.SOFT_DELETE)` (etc.) immediately before `repository.save(...)`.
The listener consumes the hint via `AuditContext.consumeOrDefault(AuditAction.UPDATE)`, which
clears it after one read.

**Rationale**:
- A `@PostUpdate` callback receives the entity but not the previous state. It cannot intrinsically
  know whether `Project.deletedAt` changed from `null → now()` (a `SOFT_DELETE`) versus a generic
  field update.
- Only the calling service has the intent. A thread-local hint is the minimum coupling between
  service and listener — neither has to change their signature.
- The consume-and-clear semantics prevent a hint set in one request from leaking into a
  subsequent request handled by the same thread (Tomcat reuses worker threads).

**Alternatives considered**:
- **Hibernate `PostUpdateEventListener` (lower-level than JPA) with access to `oldState` /
  `state` arrays.** Rejected — state slot indices depend on entity field ordering and break
  silently if a field is added; couples audit logic to entity layout.
- **Dedicated repository methods (`softDeleteAndAudit`, `restoreAndAudit`).** Rejected — couples
  persistence to audit concerns, breaks the service-as-orchestrator pattern, and forces every
  future "semantic action" to grow a new repository method.
- **Service-level explicit `auditLogService.record(...)` calls (bypassing the listener for these
  actions).** Rejected — creates two write paths and risks duplicate entries when both the
  listener and the explicit call fire.

---

### Decision 4 — Response shape: plain JSON array, not the project's `PagedResponse` envelope

**Decision**: `GET /audit-logs` returns a plain JSON array of `AuditLogResponse` objects per the
canonical README template. No pagination envelope, no `page`/`pageSize`/`total` fields.

**Rationale**:
- Constitution Principle IV makes the README response templates authoritative when they exist.
  The README's "Audit Log APIs" row specifies a plain array literal `[ { ... } ]`.
- Spec FR-005 was drafted before the README was reconciled. The plan honours the README and
  flags the spec deviation in Complexity Tracking; the spec will be amended at the next
  clarification round to remove the pagination assumption.
- The same principle has already been applied elsewhere in the project (e.g., all `POST`/`DELETE`
  endpoints return `200 OK` per the README, not the standard REST `201`/`204`).

**Alternatives considered**:
- **Return `PagedResponse<AuditLogResponse>`.** Rejected — silently breaks the documented
  contract clients were given and violates Principle IV.
- **Return the array but add `?page` / `?pageSize` query params with HTTP `Link` headers.**
  Rejected — not in the README; same Principle IV concern; adds API surface that no caller
  asked for.

---

### Decision 5 — Filter query parameters: exactly `entityType`, `entityId`, `action`, `actor`

**Decision**: Expose exactly the four query parameters listed in the README (and in the user's
plan input). Do not expose a `performedBy` / `actorId` filter.

**Rationale**:
- The README enumerates exactly these four. Principle IV: README is canonical.
- The user's plan input independently enumerates these four. Both sources agree.
- The spec's FR-007 referenced filtering "by who performed it" which more naturally maps to
  `performedBy`, but on inspection the spec was over-broad relative to the canonical contract.
  The plan defers reconciliation to a spec amendment.
- AND-combination semantics are inherent to a `Specification` composed with `and(...)`. Missing
  filters become no-op specifications.

**Alternatives considered**:
- **Add a fifth `performedBy` filter to satisfy spec FR-007.** Rejected — extends the documented
  API surface without authority; Principle IV trumps the spec.
- **Replace `actor` with `performedBy` (interpreting the user's "actor" as a typo).** Rejected —
  the user's plan input also defined `actor` as a USER/SYSTEM enum, not a user id, so both
  README and user input agree the param is the enum.

---

### Decision 6 — No foreign keys on `audit_logs.performed_by` and `audit_logs.entity_id`

**Decision**: Store both as plain `BIGINT` columns with no `REFERENCES` clause and no JPA
relationship.

**Rationale**:
- FR-011 requires audit entries to remain readable after the referenced user or entity is
  deleted. A foreign key with default `ON DELETE` behaviour would either block the deletion
  (`RESTRICT`) or remove the audit entry (`CASCADE`) — both unacceptable.
- `ON DELETE SET NULL` would preserve the entry but lose the historical `performedBy`, also
  unacceptable.
- Indexes on these columns deliver the query performance benefit; the FK constraint adds nothing
  this feature wants.

**Alternatives considered**:
- **FK with `ON DELETE NO ACTION` + deferred constraint.** Rejected — relies on application
  discipline to never delete a referenced row; defeats the purpose of having an FK.
- **Audit a separate "soft tombstone" of every user/entity on deletion.** Rejected — over-engineered;
  audit entry already records the DELETE action, which is the historical fact that matters.

---

### Decision 7 — `details` JSONB column added now, populated nowhere in v1

**Decision**: Add the `details JSONB NULL` column in the V8 migration; map it on the entity via
`@JdbcTypeCode(SqlTypes.JSON)` to `Map<String, Object>`; do not populate it in any v1 code path;
do not expose it in `AuditLogResponse`.

**Rationale**:
- The user explicitly directed the column be present, forward-compatibly, for later phases that
  will capture before/after diffs.
- Adding a JSONB column to an empty table now is cheap. Adding it later, after the table has
  grown, would be a non-trivial migration (especially with `NOT NULL` semantics).
- Hibernate 6's native `@JdbcTypeCode(SqlTypes.JSON)` support means we do **not** need
  `hibernate-types-60` or a custom `UserType`.

**Alternatives considered**:
- **Omit the column; add it later if/when needed.** Rejected — see migration-cost note above.
- **Populate it now with `{ "entityClass": "Ticket" }` as a smoke value.** Rejected — adds code
  the feature does not require; risks shaping the column for a use case different from the
  later real one.

---

### Decision 8 — ADMIN-only read; no write endpoints at all

**Decision**: `GET /audit-logs` is gated by `@PreAuthorize("hasAuthority('ADMIN')")`. No other
HTTP method is defined on the resource.

**Rationale**:
- Spec FR-006 explicitly requires admin-only access.
- `@EnableMethodSecurity` is already on `SecurityConfiguration`, so the annotation works
  immediately.
- Authority spelling is `ADMIN` (no `ROLE_` prefix) — matches ADR-001 and the existing
  `IssueFlowUserDetailsService` mapping.
- Immutability (FR-010, FR-013) is enforced by *absence*: not defining a `@PostMapping` /
  `@PutMapping` / `@DeleteMapping` means Spring returns `405 Method Not Allowed`, which is the
  desired behaviour. The `updatable = false` annotations on every audit column add a
  second layer of protection at the persistence boundary.

**Alternatives considered**:
- **Permit non-admins to read entries they performed themselves.** Rejected — out of scope per
  spec assumptions; introduces row-level filtering complexity for no v1 requirement.
- **Add a `@DenyAll`-style controller method that returns 405 explicitly.** Rejected —
  unnecessary; Spring MVC already returns 405 for absent method mappings.
