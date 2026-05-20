# Phase 1 — Data Model: Project Skeleton

This feature introduces **no business entities** and **no JPA `@Entity`
classes**. The "data" at this layer is operational state and the shape of
two transport objects.

## Domain entities

None. The first business entity (`User`) lands in a later feature.

## Transport objects

### `HealthResponse` (response body for `GET /health`)

A flat record (Java `record`, not a JPA entity, not Lombok) representing the
application's view of its own liveness and that of its dependencies.

| Field         | Type             | Meaning                                                     | Source            |
|---------------|------------------|-------------------------------------------------------------|-------------------|
| `status`      | `String`         | Overall status: `"UP"` or `"DOWN"`.                          | Aggregated        |
| `components`  | `Map<String, ComponentStatus>` | Per-dependency status. Keys: `"db"`, `"application"`. | Health indicators |
| `timestamp`   | `Instant`        | When the health was sampled (UTC, ISO-8601).                 | `Clock.systemUTC()` |

Nested `ComponentStatus`:

| Field        | Type     | Meaning                                                            |
|--------------|----------|--------------------------------------------------------------------|
| `status`     | `String` | `"UP"` or `"DOWN"`.                                                |
| `detail`     | `String` | Optional human-readable hint. Empty when the component is healthy. |

JSON example (healthy):

```json
{
  "status": "UP",
  "components": {
    "application": { "status": "UP", "detail": "" },
    "db":          { "status": "UP", "detail": "" }
  },
  "timestamp": "2026-05-20T10:42:13.512Z"
}
```

JSON example (database down):

```json
{
  "status": "DOWN",
  "components": {
    "application": { "status": "UP",   "detail": "" },
    "db":          { "status": "DOWN", "detail": "Connection refused: localhost:5432" }
  },
  "timestamp": "2026-05-20T10:42:13.512Z"
}
```

> **Implementation note**: If the implementation chooses to expose
> Actuator's `/health` directly rather than wrap it, the actual JSON keys
> will be Actuator's (`status`, `components`, where each component reports
> `status` and optionally `details`). The contract test in
> [contracts/health.openapi.yaml](contracts/health.openapi.yaml) is written
> against the wrapped shape above so the project owns its public schema.

### `ProblemDetail` (RFC 7807 error envelope)

Spring 6's built-in `org.springframework.http.ProblemDetail` is used
verbatim. Fields, all RFC 7807-compliant:

| Field      | Type    | Meaning                                                                                                  |
|------------|---------|----------------------------------------------------------------------------------------------------------|
| `type`     | `URI`   | Stable identifier for the error class, e.g. `https://issueflow.att.com/problems/not-found`.              |
| `title`    | `String`| Short, human-readable summary, e.g. `"Resource not found"`.                                              |
| `status`   | `int`   | HTTP status code mirrored into the body.                                                                 |
| `detail`   | `String`| Human-readable explanation specific to this occurrence, e.g. `"Ticket 42 does not exist."`.              |
| `instance` | `URI`   | URI reference identifying the specific occurrence, defaulted to the request path.                        |
| `properties` | `Map<String, Object>` | Extension members (e.g., `errors: [...]` for validation failures).                              |

`ErrorType` constants (stable `type` URIs) defined in
`common.error.ErrorType`:

| Constant              | Value                                                       | Used for                                               |
|-----------------------|-------------------------------------------------------------|--------------------------------------------------------|
| `NOT_FOUND`           | `https://issueflow.att.com/problems/not-found`              | 404 from `NoResourceFoundException` and future lookups |
| `VALIDATION_FAILED`   | `https://issueflow.att.com/problems/validation-failed`      | 422 from `MethodArgumentNotValidException`             |
| `MALFORMED_REQUEST`   | `https://issueflow.att.com/problems/malformed-request`      | 400 from `HttpMessageNotReadableException`             |
| `CONFLICT`            | `https://issueflow.att.com/problems/conflict`               | 409 (reserved; no current callers, scaffolded for later) |
| `INTERNAL_ERROR`      | `https://issueflow.att.com/problems/internal-error`         | Generic 500 fallback                                   |

> The `att.com` host in the `type` URI is a stable identifier — it does not
> need to resolve. Per RFC 7807 §3.1, the `type` URI's only requirement is
> that it be a stable identifier for the problem class.

## Operational components (no schema, just lifecycle)

Repeated from the spec for completeness:

- **Application Process**: lifecycle `starting → ready → stopping`. Verified
  via the `application` component in `HealthResponse`.
- **Database Instance**: lifecycle `starting → accepting connections →
  stopping`. Verified via the `db` component, which executes Actuator's
  default `DataSourceHealthIndicator` (`SELECT 1`).

## State transitions

None at this layer. Health is sampled on each `GET /health` request; there
is no persisted state.

## Validation rules

None at this layer. Validation lives at controller boundaries and there are
no controllers in this feature that accept input bodies. The
`GlobalExceptionHandler` is *prepared* to map
`MethodArgumentNotValidException` to a 422 ProblemDetail so the very first
input-accepting endpoint in a future feature gets correct behavior for free.
