# Quickstart: Ticket Comments

## Prerequisites

Same as the main project — see `run.md`.

---

## Key Implementation Notes

### 1. Enable JPA Auditing

Add `@EnableJpaAuditing` to `IssueFlowApplication`:

```java
@SpringBootApplication
@EnableJpaAuditing                         // ← add this
@EnableConfigurationProperties(JwtProperties.class)
public class IssueFlowApplication { ... }
```

`@EnableJpaAuditing` is needed for `@CreatedDate` and `@LastModifiedDate` on the `Comment` entity.
Existing entities use `@PrePersist`/`@PreUpdate` — they are unaffected.

---

### 2. Flyway Migration

Check the highest existing version under `src/main/resources/db/migration/` and name the new file `V<next>__comments.sql`. Expected next version is `V7`.

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

---

### 3. Getting the Authenticated User in the Controller

```java
@PostMapping
public CommentResponse createComment(
        @PathVariable Long ticketId,
        @Valid @RequestBody CreateCommentRequest request,
        @AuthenticationPrincipal UserDetails principal) {
    return commentService.createComment(ticketId, request, principal.getUsername());
}
```

The service then loads the `User` by username:

```java
User author = userRepository.findByUsername(username)
        .orElseThrow(() -> new NotFoundException("User not found: " + username));
```

---

### 4. Optimistic Locking Flow

`UpdateCommentRequest` carries `version`. The service:

1. Loads the comment by ID → throws `NotFoundException` if absent.
2. Checks `request.version().equals(comment.getVersion())` → throws `StaleVersionException` if mismatched (mapped to 409 by `GlobalExceptionHandler`).
3. Updates `comment.setContent(request.content())`.
4. Calls `commentRepository.save(comment)` — JPA `@Version` auto-increments and provides the concurrent-modification safety net.

---

### 5. Ticket Existence Validation

The service validates the ticket before creating or listing comments:

```java
Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
        .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
```

---

### 6. Running the Postman Tests

After the app is running:

```bash
newman run postman/comments.postman_collection.json \
    --environment postman/issueflow.local.postman_environment.json
```

---

### 7. Endpoints at a Glance

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/tickets/{ticketId}/comments` | List comments (paginated, `createdAt` ASC) |
| `POST` | `/tickets/{ticketId}/comments` | Post a comment |
| `PATCH` | `/tickets/{ticketId}/comments/{commentId}` | Edit a comment (version required) |
| `DELETE` | `/tickets/{ticketId}/comments/{commentId}` | Delete a comment |
