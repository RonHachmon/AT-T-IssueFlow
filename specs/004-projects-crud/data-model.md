# Data Model: Projects CRUD

**Branch**: `004-projects-crud` | **Date**: 2026-05-21

## Entity: Project

```
projects
├── id          BIGSERIAL PRIMARY KEY
├── name        VARCHAR(255) NOT NULL
├── description TEXT
├── owner_id    BIGINT NOT NULL REFERENCES users(id)
├── created_at  TIMESTAMPTZ NOT NULL
├── updated_at  TIMESTAMPTZ NOT NULL
└── deleted_at  TIMESTAMPTZ           -- NULL = active; non-NULL = soft-deleted
```

### Constraints

| Constraint | Type | Rule |
|---|---|---|
| `pk_projects` | Primary key | `id` |
| `fk_projects_owner` | Foreign key | `owner_id REFERENCES users(id)` |
| `ux_projects_name_active` | Partial unique index | `LOWER(name) WHERE deleted_at IS NULL` |

### Validation rules (application layer)

| Field | Rule |
|---|---|
| `name` | Required, non-blank, max 255 characters |
| `description` | Optional (may be null or empty) |
| `ownerId` | Required; must reference an existing, active user |

### State transitions

```
[active]  ──DELETE──>  [soft-deleted]
            (sets deleted_at = now())
```

Restore (setting `deleted_at = NULL`) is explicitly **out of scope** for this phase.

---

## Relationships

```
users  (1) ──── (N)  projects
         owner_id FK
```

- One user may own many projects.
- A project has exactly one owner.
- Deleting the owner user is **not** addressed in this phase (no cascading behaviour is defined yet).

---

## DTO Shapes

### Request: `POST /projects`

```json
{
  "name":        "Backend Rewrite",
  "description": "Modernise the service layer",
  "ownerId":     5
}
```

- `name` — required, non-blank, max 255 chars
- `description` — optional
- `ownerId` — required Long; validated as an existing user in the service

### Request: `PATCH /projects/:projectId`

```json
{
  "name":        "New Name",
  "description": "Updated description"
}
```

- Both fields optional individually; at least one must be non-null (enforced by `@AssertTrue`).
- `ownerId` is **not** present — owner transfer is out of scope.

### Response: all project-returning endpoints

```json
{
  "id":          1,
  "name":        "Backend Rewrite",
  "description": "Modernise the service layer",
  "ownerId":     5
}
```

Matches the README Projects APIs response template exactly. Timestamps are stored internally but not surfaced to clients (consistent with `UserResponse`).

---

## Flyway Migration: `V5__projects.sql`

```sql
CREATE TABLE projects (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(255)    NOT NULL,
    description TEXT,
    owner_id    BIGINT          NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL,
    deleted_at  TIMESTAMPTZ
);

-- Partial unique index: name uniqueness enforced on active projects only.
-- Allows a deleted project's name to be reclaimed.
CREATE UNIQUE INDEX ux_projects_name_active
    ON projects (LOWER(name))
    WHERE deleted_at IS NULL;
```

---

## Java Entity: `Project.java`

```java
@Entity
@Table(name = "projects")
@Getter @Setter @NoArgsConstructor
public class Project {

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PrePersist void onCreate()  { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
  @PreUpdate  void onUpdate()  { updatedAt = Instant.now(); }
}
```

---

## Repository: `ProjectRepository.java`

Key query methods (Spring Data derived queries):

| Method | SQL intent |
|---|---|
| `findByIdAndDeletedAtIsNull(Long id)` | Fetch one active project |
| `findAllByDeletedAtIsNullOrderByIdAsc()` | List all active projects |
| `existsByNameIgnoreCaseAndDeletedAtIsNull(String name)` | Uniqueness pre-check |

Soft-delete is applied by the service (`project.setDeletedAt(Instant.now())` then `save`), not via a derived delete query.
