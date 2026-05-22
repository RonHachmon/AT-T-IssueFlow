# Data Model: Ticket Comments

## Entity: Comment

**Table**: `comments`  
**Package**: `com.att.tdp.issueflow.comment`

### Fields

| Java Field   | Column        | SQL Type           | Nullable | Constraints                          |
|--------------|---------------|--------------------|----------|--------------------------------------|
| `id`         | `id`          | `BIGSERIAL`        | No       | Primary key, auto-generated          |
| `content`    | `content`     | `TEXT`             | No       | Non-blank; max 2000 characters       |
| `ticket`     | `ticket_id`   | `BIGINT`           | No       | FK → `tickets.id`; immutable         |
| `author`     | `author_id`   | `BIGINT`           | No       | FK → `users.id`; immutable; set from JWT |
| `createdAt`  | `created_at`  | `TIMESTAMPTZ`      | No       | Set once on insert via `@CreatedDate` |
| `updatedAt`  | `updated_at`  | `TIMESTAMPTZ`      | No       | Refreshed on every update via `@LastModifiedDate` |
| `version`    | `version`     | `BIGINT`           | No       | JPA optimistic-lock counter; starts at 0; auto-incremented by JPA |

### Annotations

```
@Entity
@Table(name = "comments")
@EntityListeners(AuditingEntityListener.class)   ← required for @CreatedDate / @LastModifiedDate
@Getter @Setter @NoArgsConstructor               ← Lombok
```

### Relationships

```
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "ticket_id", nullable = false, updatable = false)
private Ticket ticket;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "author_id", nullable = false, updatable = false)
private User author;
```

### Lifecycle

- `createdAt` and `updatedAt` are managed by Spring Data JPA auditing (`@EnableJpaAuditing` added to `IssueFlowApplication`).
- `version` is managed by JPA (`@Version`); increments automatically on each successful `UPDATE`.
- There is **no `deletedAt` column** — comments are hard-deleted.

---

## Flyway Migration

**File**: `src/main/resources/db/migration/V7__comments.sql`

```sql
CREATE TABLE comments (
    id         BIGSERIAL    PRIMARY KEY,
    content    TEXT         NOT NULL,
    ticket_id  BIGINT       NOT NULL REFERENCES tickets(id),
    author_id  BIGINT       NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_comments_ticket_id ON comments(ticket_id);
```

> **Note**: Verify the next available migration version against existing files under `src/main/resources/db/migration/` before finalising the filename.

---

## DTOs

### CreateCommentRequest

```
record CreateCommentRequest(
    @NotBlank @Size(max = 2000) String content
)
```

### UpdateCommentRequest

```
record UpdateCommentRequest(
    @NotBlank @Size(max = 2000) String content,
    @NotNull Long version
)
```

### CommentResponse

```
record CommentResponse(
    Long id,
    String content,
    Long ticketId,     // mapped from comment.ticket.id
    Long authorId,     // mapped from comment.author.id
    Instant createdAt,
    Instant updatedAt,
    Long version
)
```

---

## New Error Classes

### StaleVersionException

**Package**: `com.att.tdp.issueflow.common.error`  
**Maps to**: `409 Conflict` in `GlobalExceptionHandler`

Thrown by `CommentService.updateComment()` when `request.version()` does not match the stored `comment.getVersion()`, indicating the client is working from a stale read.

---

## Repository

```
interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId, Pageable pageable);
}
```

No soft-delete filter methods are needed — comments are physically deleted.

---

## MapStruct Mapper

`CommentMapper` maps `Comment` → `CommentResponse`:

```
@Mapping(target = "ticketId", source = "ticket.id")
@Mapping(target = "authorId", source = "author.id")
CommentResponse toResponse(Comment comment);
```

And `CreateCommentRequest` → `Comment` (server-managed fields ignored):

```
@Mapping(target = "id",        ignore = true)
@Mapping(target = "ticket",    ignore = true)
@Mapping(target = "author",    ignore = true)
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
@Mapping(target = "version",   ignore = true)
Comment toEntity(CreateCommentRequest request);
```

---

## Entity Relationships Summary

```
User (existing)
 └── authorId ─── Comment ─── ticketId ──→ Ticket (existing)
                                               └── projectId ──→ Project (existing)
```

No changes to `User`, `Ticket`, or `Project` entities.
