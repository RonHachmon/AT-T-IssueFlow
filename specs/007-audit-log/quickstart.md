# Audit Log — Developer Quickstart

## What this feature gives you

- Every successful `CREATE` / `UPDATE` / `DELETE` (or `SOFT_DELETE` / `RESTORE` / `STATUS_CHANGE`)
  on a `User`, `Project`, `Ticket`, or `Comment` writes an immutable row to `audit_logs`.
- `GET /audit-logs` (ADMIN-only) returns the entries, newest first, optionally filtered by
  `entityType`, `entityId`, `action`, or `actor`.

## Running it locally

1. `docker compose up -d` to start Postgres on `:5432`.
2. `./mvnw spring-boot:run` (the V8 migration applies automatically on boot).
3. Log in as the seeded admin to obtain a JWT — see [run.md](../../run.md).
4. Hit the endpoint:
   ```bash
   curl -H "Authorization: Bearer $ADMIN_TOKEN" \
        "http://localhost:8080/audit-logs?entityType=TICKET&action=CREATE"
   ```

## Auditing a new entity type

1. Add a value to `AuditEntityType` (e.g., `ATTACHMENT`).
2. Add the matching `case` to `EntityClassifier.classify(...)` / `extractId(...)`.
3. Annotate the new entity with `@EntityListeners(AuditableEntityListener.class)` (or extend an
   existing `@EntityListeners` array).

That is the entire change — the listener, service, controller, and migration all stay the same.

## Emitting a semantic action (not just `CREATE`/`UPDATE`/`DELETE`)

```java
@Transactional
public void softDelete(Long id) {
  Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(id)
      .orElseThrow(() -> new NotFoundException("Ticket", id));

  ticket.setDeletedAt(Instant.now());
  AuditContext.hint(AuditAction.SOFT_DELETE);   // <-- consumed by @PostUpdate
  ticketRepository.save(ticket);
}
```

Rules:
- Call `AuditContext.hint(...)` **immediately before** `repository.save(...)`.
- The hint is one-shot — it is consumed and cleared after a single listener callback. If you do
  not call `save(...)` after hinting (e.g., an exception is thrown first), the hint stays in the
  thread until the next save. Set hints inside the transactional method to avoid leaking across
  requests in pooled-thread containers; the next `consumeOrDefault` will clear it.

## Why the listener calls a static bean lookup

JPA instantiates entity listeners by reflection; Spring does not inject them. The
`SpringContextHolder` bean caches the running `ApplicationContext` in a static field at startup so
the listener can call `SpringContextHolder.bean(AuditLogService.class)` at callback time. This is
the smallest workable bridge between JPA's reflective construction and Spring's DI graph.

## Why the response is a JSON array, not `PagedResponse`

The project's `README.md` "Audit Log APIs" table is the canonical contract per Constitution
Principle IV. It specifies a plain JSON array. The spec's mention of a paginated envelope is a
drafting bug; the plan and implementation follow the README.

## Testing checklist

- `AuditLogServiceTest` — write path + filter combinations (pure JVM, no Spring context).
- `AuditableEntityListenerTest` — hint consumption + default `UPDATE` fallback.
- `SecurityUtilTest` — anonymous and null `Authentication`, `UserDetails` happy path.
- `AuditLogControllerAuthorizationTest` — 401 / 403 / 200 with standalone `MockMvc`.
- `audit-logs.postman_collection.json` — wire-level: all four filters, 401, 403, 405 on writes,
  end-to-end smoke (admin POSTs a project, then sees the matching audit entry).

## What is NOT in scope for v1

- Before/after value diffs — the `details` JSONB column exists but is left `null`.
- Login events — auth is not audited.
- Date-range filters — only the four documented filters are supported.
- Pagination — entries return as a flat array.
- Non-admin access — only `ADMIN` can read.
