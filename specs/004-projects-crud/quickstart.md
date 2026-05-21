# Quickstart: Projects CRUD

**Branch**: `004-projects-crud` | **Date**: 2026-05-21

## Prerequisites

- Java 21+, Maven wrapper (`./mvnw`)
- PostgreSQL running (see `run.md`)
- `APP_SECURITY_JWT_SECRET` set (see `run.md`)
- A valid bearer token from `POST /auth/login`

## Build

```bash
./mvnw verify
```

## Smoke-test the endpoints

Replace `<TOKEN>` with a JWT from `POST /auth/login`.

### Create a project

```bash
curl -s -X POST http://localhost:8080/projects \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Backend Rewrite","description":"Modernise the service layer","ownerId":1}' | jq .
```

Expected: `200 OK` → `{ "id": 1, "name": "Backend Rewrite", "description": "...", "ownerId": 1 }`

### List projects

```bash
curl -s http://localhost:8080/projects \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

Expected: `200 OK` → `[{ "id": 1, ... }]`

### Get by ID

```bash
curl -s http://localhost:8080/projects/1 \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

Expected: `200 OK` → `{ "id": 1, "name": "...", "description": "...", "ownerId": 1 }`

### Update name

```bash
curl -s -X PATCH http://localhost:8080/projects/1 \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"New Name"}' -o /dev/null -w "%{http_code}\n"
```

Expected: `200` (empty body)

### Soft-delete

```bash
curl -s -X DELETE http://localhost:8080/projects/1 \
  -H "Authorization: Bearer <TOKEN>" -o /dev/null -w "%{http_code}\n"
```

Expected: `200` (empty body)

### Verify soft-delete

```bash
# Deleted project absent from list
curl -s http://localhost:8080/projects -H "Authorization: Bearer <TOKEN>" | jq .
# Returns empty array [] (if only one project was created)

# Deleted project returns 404
curl -s http://localhost:8080/projects/1 -H "Authorization: Bearer <TOKEN>" -o /dev/null -w "%{http_code}\n"
# Returns 404
```

## Validation checks

```bash
# Missing name → 400
curl -s -X POST http://localhost:8080/projects \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"ownerId":1}' | jq .type,.status

# Unknown ownerId → 404
curl -s -X POST http://localhost:8080/projects \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"X","ownerId":99999}' | jq .status

# Duplicate name → 409
curl -s -X POST http://localhost:8080/projects \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Backend Rewrite","ownerId":1}' | jq .status

# No auth → 401
curl -s http://localhost:8080/projects -w "%{http_code}\n" -o /dev/null
```

## Run unit tests

```bash
./mvnw test -pl . -Dtest=ProjectServiceTest
```
