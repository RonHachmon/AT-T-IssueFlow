# Quickstart — Users CRUD

The five-minute check that proves Users CRUD works end-to-end. Assumes
the skeleton (feature 001) is already running per `run.md`.

## Prerequisites

- Postgres + the app running locally (see [run.md](../../run.md)).
- `curl` and `jq` available, or Postman with the project's collection.

## 1. Create a user

```bash
curl -s -i -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "fullName": "Alice Doe",
    "role": "DEVELOPER"
  }'
```

Expected:

```http
HTTP/1.1 201 Created
Location: /users/1
Content-Type: application/json

{
  "id": 1,
  "username": "alice",
  "email": "alice@example.com",
  "fullName": "Alice Doe",
  "role": "DEVELOPER",
  "createdAt": "2026-05-21T10:00:00Z",
  "updatedAt": "2026-05-21T10:00:00Z"
}
```

Capture the id:

```bash
USER_ID=1
```

## 2. Fetch by id

```bash
curl -s -i http://localhost:8080/users/$USER_ID
```

Expected: `200 OK` with the same body shape as step 1.

```bash
curl -s -i http://localhost:8080/users/999999
```

Expected: `404 Not Found` with `application/problem+json` body, `type`
ending in `/not-found`, `detail` naming the missing id.

## 3. List

```bash
curl -s http://localhost:8080/users | jq .
```

Expected:

```json
{
  "data": [
    { "id": 1, "username": "alice", "...": "..." }
  ],
  "page": 0,
  "pageSize": 20,
  "total": 1
}
```

Page out:

```bash
curl -s 'http://localhost:8080/users?page=10&pageSize=20' | jq .
```

Expected: `200` with `data: []`, `page: 10`, `total: 1`.

## 4. Update

```bash
curl -s -i -X PATCH http://localhost:8080/users/$USER_ID \
  -H 'Content-Type: application/json' \
  -d '{ "role": "ADMIN" }'
```

Expected: `204 No Content`.

Verify:

```bash
curl -s http://localhost:8080/users/$USER_ID | jq .role
# "ADMIN"
```

## 5. Validation errors

### Duplicate username

```bash
curl -s -i -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "ALICE",
    "email": "other@example.com",
    "fullName": "Other",
    "role": "DEVELOPER"
  }'
```

Expected: `409 Conflict`, `application/problem+json`, `detail` mentions
`username`. The uppercase variant is still a duplicate because uniqueness
is case-insensitive.

### Malformed email

```bash
curl -s -i -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "bob",
    "email": "not-an-email",
    "fullName": "Bob",
    "role": "DEVELOPER"
  }'
```

Expected: `422 Unprocessable Entity` with `errors: [{ field: "email", message: "..." }]`.

### Empty update

```bash
curl -s -i -X PATCH http://localhost:8080/users/$USER_ID \
  -H 'Content-Type: application/json' \
  -d '{}'
```

Expected: `422 Unprocessable Entity` — at least one of `fullName` or
`role` is required.

### Try to change username via PATCH

```bash
curl -s -i -X PATCH http://localhost:8080/users/$USER_ID \
  -H 'Content-Type: application/json' \
  -d '{ "username": "renamed" }'
```

Expected: `422 Unprocessable Entity` — `username` is immutable; the
field is silently ignored on the DTO, so the resulting validation
failure is "no modifiable fields set" (same path as the empty PATCH).

## 6. Delete

```bash
curl -s -i -X DELETE http://localhost:8080/users/$USER_ID
```

Expected: `204 No Content`. A subsequent `GET /users/$USER_ID` returns
`404`, and the list endpoint reports `total: 0`.

## 7. Restart-survival

Stop the app (`Ctrl-C` in the `spring-boot:run` terminal), then re-run
it. Re-issue step 2 with a previously-created id — the user is still
there.

## 8. Postman

```bash
npx newman run postman/issueflow.postman_collection.json \
  -e postman/issueflow.local.postman_environment.json \
  --folder "Users"
```

Expected: all "Users" requests pass. The DB-down variant in the Health
folder stays manual as before.

## When this is "done"

- Five endpoints all return the constitutional status codes
  (201, 200, 200, 204, 204) — never `200 OK` for create or delete.
- Validation errors carry `application/problem+json` with an `errors`
  array naming the offending fields.
- Case-insensitive duplicate detection rejects `ALICE` after `alice` is
  created.
- The application restarts cleanly and persisted users survive.

## Troubleshooting

| Symptom                                                  | Likely cause                                          | Fix                                                              |
|----------------------------------------------------------|-------------------------------------------------------|------------------------------------------------------------------|
| `409` immediately on first create                        | Stale `users` rows from a previous run                | `docker compose down -v` to drop the volume, then `docker compose up -d`. |
| `MapStruct` generates a no-op mapper                     | `mapstruct-processor` listed before `lombok` in `pom.xml` | Reorder annotation processors: Lombok first, then MapStruct.     |
| `JpaSystemException` on `User` save                      | Missing `@PrePersist`/`@PreUpdate` populating timestamps | Ensure both lifecycle methods are on the entity.                  |
| `422` on every create with `errors: [{ field: "role" ... }]` | Sent `role` as lowercase `developer`                  | Use the uppercase enum value `DEVELOPER`.                         |
