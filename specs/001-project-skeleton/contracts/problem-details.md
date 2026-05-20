# Contract — RFC 7807 Problem Details

Every error response from the IssueFlow API uses
`application/problem+json` per [RFC 7807]. This contract is enforced by a
single `@RestControllerAdvice` (`common.error.GlobalExceptionHandler`) and
inherited by every future endpoint — controllers do not catch generic
exceptions themselves.

This contract is scaffolded in the project-skeleton feature so the first
business endpoint added in a later feature produces correctly-shaped errors
without any additional work.

## Envelope

```json
{
  "type":     "https://issueflow.att.com/problems/<slug>",
  "title":    "<short human-readable summary>",
  "status":   <HTTP status code>,
  "detail":   "<occurrence-specific explanation>",
  "instance": "<request path>"
}
```

Validation failures (Bean Validation, `MethodArgumentNotValidException`)
additionally include an `errors` extension array:

```json
{
  "type":   "https://issueflow.att.com/problems/validation-failed",
  "title":  "Validation failed",
  "status": 422,
  "detail": "One or more fields failed validation.",
  "instance": "/users",
  "errors": [
    { "field": "email", "message": "must be a well-formed email address" }
  ]
}
```

## Stable `type` URIs

The `type` URI is the **stable identifier** for the error class. Clients
key off it; humans read `title`/`detail`. URIs are defined in
`common.error.ErrorType` as constants — they MUST NOT be re-derived
ad-hoc at error sites.

| `type`                                                    | HTTP status | Triggers                                                                 |
|-----------------------------------------------------------|-------------|--------------------------------------------------------------------------|
| `https://issueflow.att.com/problems/malformed-request`    | `400`       | `HttpMessageNotReadableException` (bad JSON, etc.)                       |
| `https://issueflow.att.com/problems/not-found`            | `404`       | `NoResourceFoundException`, future `EntityNotFoundException` subclasses  |
| `https://issueflow.att.com/problems/conflict`             | `409`       | Reserved — future state-transition violations, duplicate-resource errors |
| `https://issueflow.att.com/problems/validation-failed`    | `422`       | `MethodArgumentNotValidException`, `ConstraintViolationException`        |
| `https://issueflow.att.com/problems/internal-error`       | `500`       | Catch-all fallback (`Exception` handler)                                 |

## Status-code mapping rules

Status codes follow constitution Principle IV. Reviewers MUST cite this
table when rejecting deviations.

| Cause                                                 | Status |
|-------------------------------------------------------|--------|
| Syntactically invalid request (bad JSON, wrong type)  | `400`  |
| Missing/invalid authentication (future feature)       | `401`  |
| Authenticated but not authorized (future feature)     | `403`  |
| Unknown route or unknown resource ID                  | `404`  |
| Verb not allowed on the route                         | `405`  |
| Duplicate resource / illegal state transition         | `409`  |
| Request body fails semantic validation                | `422`  |
| Unhandled internal failure                            | `500`  |

## Rules

1. **Never leak stack traces** in `detail`. Logs hold the stack; the body
   holds a sanitized summary.
2. **Never leak secrets** in `detail` or `errors[*].message`. Even the
   field name `password` is fine to mention; the value is not.
3. **`instance` is the request path** unless the handler has a more
   specific occurrence URI. Spring populates this by default; do not
   override unless you have a reason.
4. **One advice, one place**: `GlobalExceptionHandler` is the only class
   that catches exceptions to convert them into HTTP responses. Service
   classes throw; controllers do not catch.

## What the skeleton ships vs. what later features add

- **Skeleton ships**:
  - `GlobalExceptionHandler` with `@ExceptionHandler` methods for
    `MethodArgumentNotValidException`, `HttpMessageNotReadableException`,
    `NoResourceFoundException`, and `Exception` (the catch-all).
  - `ErrorType` constants.
  - `ProblemDetailFactory` helper for building `ProblemDetail` instances
    with the right `type` URI.
- **Skeleton does not ship**:
  - Any domain-specific exception classes (e.g., `TicketNotFoundException`).
    Those land with the features that introduce them; each is mapped to an
    existing `ErrorType` constant via a one-line `@ExceptionHandler`.

[RFC 7807]: https://datatracker.ietf.org/doc/html/rfc7807
