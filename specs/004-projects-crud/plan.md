# Implementation Plan: Projects — Create, Read, Update, Soft-Delete

**Branch**: `004-projects-crud` | **Date**: 2026-05-21 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/004-projects-crud/spec.md`

## Summary

Add full CRUD for `Project` entities — the containers that will later hold tickets. The system exposes five endpoints (`POST`, `GET` list, `GET` by ID, `PATCH`, `DELETE`) under `/projects`. Deletion is soft: a `deleted_at` timestamp is set, and deleted projects are invisible to all standard reads. All status codes follow the README Projects APIs table, which defines `200 OK` for every operation (including `POST` and `DELETE`).

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.4.2
**Primary Dependencies**: Spring Data JPA, Spring Web, Bean Validation (jakarta.validation), MapStruct, Lombok — all already in `pom.xml`; no new dependencies required
**Storage**: PostgreSQL — new Flyway migration `V5__projects.sql` adds the `projects` table with a `deleted_at` column and a partial unique index on `LOWER(name) WHERE deleted_at IS NULL`
**Testing**: JUnit 5 + Mockito (pure JVM, no Spring context); Postman collection updated with a new Projects folder
**Target Platform**: Linux container / local JVM (same as the rest of the service)
**Project Type**: REST web service
**Performance Goals**: Same as the rest of the service — standard single-node Spring Boot response times; no special requirements for this feature
**Constraints**: Soft-delete only; restore endpoints are explicitly out of scope. No pagination (README shows plain array). No owner transfer (update endpoint mutates `name`/`description` only).
**Scale/Scope**: Single-node; no distributed concerns

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Gate | Pass? |
|---|------|-------|
| I  | **Clean Code** — `ProjectService` has single-purpose methods (`create`, `getById`, `list`, `update`, `softDelete`). No mixed abstraction levels — controller delegates, service orchestrates, repository queries. No flag arguments; `deletedAt` state is modelled as a nullable `Instant`, not a boolean flag. Named constant `RESOURCE = "Project"` replaces the magic string. | [x] |
| II | **Testing Standards** — Pure-JVM `ProjectServiceTest` covers: happy-path create, duplicate-name conflict, unknown-owner create, getById happy, getById on deleted, list filters deleted, update happy, update on deleted, softDelete happy, softDelete on already-deleted. Postman: create (valid, missing-name, unknown-owner, duplicate-name), list, get by ID, get soft-deleted (404), update (valid, empty body), delete (valid, second call → 404), no-auth (401). | [x] |
| III| **Documentation Discipline** — Javadoc on every public method in `ProjectController`, `ProjectService`, `ProjectMapper`. README already contains the full Projects APIs table — no update needed. `run.md` unchanged (no new env vars). `prompts.md` entry added for this planning session. | [x] |
| IV | **API Consistency** — All five endpoints return `200 OK` per the README Projects APIs table (the canonical template overrides the REST default of `201`/`204`). Response shape matches `{ id, name, description, ownerId }`. Errors use RFC 7807 via the existing `GlobalExceptionHandler` + `ProblemDetailFactory`. DTOs (`ProjectResponse`, `CreateProjectRequest`, `UpdateProjectRequest`) at every controller boundary — no entity exposure. MapStruct `ProjectMapper` handles all mapping. `projectId` path variable name follows `<entity>Id` convention. | [x] |

## Project Structure

### Documentation (this feature)

```text
specs/004-projects-crud/
├── plan.md                     # This file
├── spec.md                     # Feature specification
├── research.md                 # Phase 0 decisions
├── data-model.md               # Phase 1 entity / migration design
├── quickstart.md               # Phase 1 smoke-test commands
├── contracts/
│   └── projects.openapi.yaml   # Phase 1 OpenAPI contract
├── checklists/
│   └── requirements.md         # Spec quality checklist
└── tasks.md                    # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
pom.xml
  (no new dependencies — Spring Data JPA, MapStruct, Bean Validation already present)

src/main/resources/db/migration/
└── V5__projects.sql            # NEW — creates projects table + partial unique index

src/main/java/com/att/tdp/issueflow/
└── project/
    ├── package-info.java       # already exists (empty stub)
    ├── Project.java            # NEW — @Entity with soft-delete column
    ├── ProjectRepository.java  # NEW — JpaRepository + derived query methods
    ├── ProjectService.java     # NEW — create / getById / list / update / softDelete
    ├── ProjectController.java  # NEW — 5 endpoints, all @ResponseStatus(OK)
    ├── ProjectMapper.java      # NEW — MapStruct toEntity / toResponse
    └── dto/
        ├── CreateProjectRequest.java   # NEW — name (required), description, ownerId
        ├── UpdateProjectRequest.java   # NEW — name (optional), description (optional)
        └── ProjectResponse.java        # NEW — id, name, description, ownerId

src/test/java/com/att/tdp/issueflow/
└── project/
    └── ProjectServiceTest.java         # NEW — 10 pure-JVM unit tests

postman/issueflow.postman_collection.json
  (updated — new "Projects" folder with 10 requests)
```

## Implementation Notes

### `Project.java` — Entity

- `@ManyToOne(fetch = FetchType.LAZY)` on `owner` field; `@JoinColumn(name = "owner_id", nullable = false)`
- `@PrePersist` sets both `createdAt` and `updatedAt`; `@PreUpdate` updates `updatedAt` only
- `deletedAt` is a plain nullable `Instant` column — no JPA lifecycle hook touches it; the service sets it explicitly on soft-delete

### `ProjectRepository.java` — Repository

Three query methods beyond the standard `JpaRepository`:

```java
Optional<Project> findByIdAndDeletedAtIsNull(Long id);
List<Project>     findAllByDeletedAtIsNullOrderByIdAsc();
boolean           existsByNameIgnoreCaseAndDeletedAtIsNull(String name);
```

Soft-delete itself is a save (not a derived delete query) to preserve entity lifecycle hooks.

### `ProjectService.java` — Service

```
create(request):
  1. Resolve owner: userRepository.findById(ownerId) → NotFoundException if absent
  2. Check duplicate name: existsByNameIgnoreCaseAndDeletedAtIsNull(name) → DuplicateResourceException
  3. Map to entity via mapper (owner reference, not ownerId)
  4. save → map to response

getById(id):
  findByIdAndDeletedAtIsNull(id) → NotFoundException if absent or deleted

list():
  findAllByDeletedAtIsNullOrderByIdAsc() → map each to response

update(id, request):
  findByIdAndDeletedAtIsNull(id) → NotFoundException
  Apply non-null fields (name, description)
  save

softDelete(id):
  findByIdAndDeletedAtIsNull(id) → NotFoundException (includes already-deleted)
  project.setDeletedAt(Instant.now())
  save
```

### `ProjectMapper.java` — MapStruct

```java
@Mapper
public interface ProjectMapper {

  @Mapping(target = "id",          ignore = true)
  @Mapping(target = "createdAt",   ignore = true)
  @Mapping(target = "updatedAt",   ignore = true)
  @Mapping(target = "deletedAt",   ignore = true)
  @Mapping(target = "owner",       source = "owner")
  Project toEntity(CreateProjectRequest request, User owner);

  @Mapping(target = "ownerId", source = "owner.id")
  ProjectResponse toResponse(Project project);
}
```

The `toEntity` method takes the resolved `User` as a second parameter so the mapper does not need to call the repository.

### `ProjectController.java` — Controller

All five methods annotated `@ResponseStatus(HttpStatus.OK)`:

| Method | Verb | Path |
|---|---|---|
| `create` | `@PostMapping` | `/projects` |
| `list` | `@GetMapping` | `/projects` |
| `getById` | `@GetMapping` | `/projects/{projectId}` |
| `update` | `@PatchMapping` | `/projects/{projectId}` |
| `softDelete` | `@DeleteMapping` | `/projects/{projectId}` |

`update` and `softDelete` return `void`. `create`, `list`, `getById` return the DTO.

### `ProjectServiceTest.java` — Unit Tests

Pure JVM — no Spring context. Mock `ProjectRepository` and `UserRepository` with Mockito.

Test cases (10):

1. `createProject_persistsAndReturnsResponse` — happy path
2. `createProject_throwsDuplicateWhenNameExists` — duplicate name pre-check
3. `createProject_throwsNotFoundWhenOwnerAbsent` — unknown `ownerId`
4. `getById_returnsProjectWhenActive`
5. `getById_throwsNotFoundWhenSoftDeleted` — `findByIdAndDeletedAtIsNull` returns empty
6. `list_excludesSoftDeletedProjects`
7. `update_appliesNameChange`
8. `update_throwsNotFoundWhenSoftDeleted`
9. `softDelete_setsDeletedAt`
10. `softDelete_throwsNotFoundOnAlreadyDeleted`

### `V5__projects.sql` — Migration

```sql
CREATE TABLE projects (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id    BIGINT       NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    deleted_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_projects_name_active
    ON projects (LOWER(name))
    WHERE deleted_at IS NULL;
```

### Postman — Projects Folder (10 requests)

1. Create project — valid → 200 with body
2. Create project — missing name → 400
3. Create project — unknown ownerId → 404
4. Create project — duplicate name → 409
5. List projects → 200 array
6. Get project by ID — active → 200
7. Get project by ID — after soft-delete → 404
8. Update project — valid → 200 empty
9. Update project — empty body → 400
10. Delete project → 200 empty; re-delete → 404
11. *(bonus)* No auth → 401

All requests inherit the collection-level bearer token variable.

## Complexity Tracking

> **No constitution violations — no entries needed.**
