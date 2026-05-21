# Contract — Canonical Pagination Envelope

Every list endpoint in IssueFlow returns the same pagination envelope.
This contract is established in feature 002 (Users CRUD) and inherited
by every future list endpoint per constitution Principle IV ("pagination
uses the same shape everywhere").

## Envelope

```json
{
  "data":     [ /* one page of T */ ],
  "page":     0,
  "pageSize": 20,
  "total":    137
}
```

| Field      | Type        | Meaning                                                              |
|------------|-------------|----------------------------------------------------------------------|
| `data`     | `array<T>`  | The rows on this page. Empty array if the page is past the end.      |
| `page`     | `integer`   | 0-indexed page number (matches Spring Data's `Pageable`).            |
| `pageSize` | `integer`   | Records per page; echoes the request, capped server-side at **100**. |
| `total`    | `integer`   | Total number of records across **all** pages (not just this page).   |

## Query parameters

| Param      | Default | Bounds      | Behavior                                            |
|------------|---------|-------------|-----------------------------------------------------|
| `page`     | `0`     | `≥ 0`       | Negative values → 422.                              |
| `pageSize` | `20`    | `1`–`100`   | Out-of-range values → 422 with field-level message. |

## Rules

1. **Empty page is success, not failure.** Requesting a page beyond the
   data returns `200` with `data: []`. It is never a `404`.
2. **`total` is the row count across all pages**, not the row count on
   this page. Clients compute `pageCount = ceil(total / pageSize)`.
3. **Sorting is per-endpoint**. The envelope does not carry a `sort`
   field; each endpoint documents its default order in its OpenAPI
   contract.
4. **`pageSize` is capped at 100**. Any request that asks for more
   returns 422 with a `pageSize` field error. Clients that need bulk
   reads should use the upcoming CSV-export endpoint, not paginate at
   `pageSize=10000`.
5. **Server may stabilize order with a tiebreaker** (e.g. `id` ascending
   after the endpoint's natural sort) so that pages don't shuffle
   between calls. Endpoints document this in their OpenAPI.

## Java representation

```java
public record PagedResponse<T>(
    List<T> data,
    int page,
    int pageSize,
    long total
) {}
```

Service code converts Spring Data's `Page<T>` to `PagedResponse<T>`:

```java
Page<User> page = userRepository.findAll(PageRequest.of(p, ps));
return new PagedResponse<>(
    page.getContent().stream().map(mapper::toResponse).toList(),
    page.getNumber(),
    page.getSize(),
    page.getTotalElements()
);
```

This conversion lives in the service layer; controllers never see
Spring Data's `Page<T>`.

## Forbidden shapes

- ❌ Spring Data's native `Page<T>` JSON (verbose, exposes
  framework-internal keys like `pageable.sort.sorted`, `numberOfElements`,
  `first`, `last`).
- ❌ Per-endpoint envelope variations (`{ items, count }`,
  `{ results, hasMore }`, etc.).
- ❌ Cursor pagination — deferred until a list endpoint has scale that
  justifies it.

## Where this lives

- **Code**: `com.att.tdp.issueflow.common.pagination.PagedResponse<T>`
  (Java record).
- **Contracts**: every list endpoint's OpenAPI references the
  `PagedEnvelope` schema shape (composed via `allOf` with the
  endpoint-specific `data` array type).
- **Tests**: `UserServiceTest.list_returnsCanonicalPagedResponse` is
  the first verification.
