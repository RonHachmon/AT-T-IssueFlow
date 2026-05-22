# Research: Ticket Comments

## Decision 1: Timestamp Management

**Decision**: Add `@EnableJpaAuditing` to `IssueFlowApplication` and use `@CreatedDate` / `@LastModifiedDate` on the Comment entity (paired with `@EntityListeners(AuditingEntityListener.class)`).

**Rationale**: The user explicitly requested this approach. It is the idiomatic Spring Data JPA pattern and does not break existing entities — Ticket, Project, and User use `@PrePersist` / `@PreUpdate` and will continue to function unmodified. `@EnableJpaAuditing` is additive.

**Alternatives considered**: Continue with `@PrePersist` / `@PreUpdate` for consistency with existing entities. Rejected because the user explicitly chose the auditing approach, and mixing is acceptable since the two mechanisms are independent.

---

## Decision 2: Delete Strategy

**Decision**: Hard delete (physical `DELETE` from the database). No `deletedAt` column on comments.

**Rationale**: The spec explicitly states comments do not need soft-delete. The comment package-info placeholder in the codebase leaves the choice open. Hard delete simplifies the repository and removes the need for `AndDeletedAtIsNull` filter methods.

**Alternatives considered**: Soft-delete for audit trail. Rejected per spec.

---

## Decision 3: Endpoint Path

**Decision**: Nest comments under `/tickets/{ticketId}/comments`.

**Rationale**: Matches the project's existing URL structure (`/tickets`, not `/projects/{projectId}/tickets`). The user's input explicitly shows `GET /tickets/{id}/comments`. Comments are a direct child of a ticket — the project scope is implicit via the ticket.

**Alternatives considered**: `/projects/{projectId}/tickets/{ticketId}/comments` (from the original spec). Rejected — the project does not use `/projects/{projectId}/tickets/...` paths; everything goes through `/tickets` directly.

---

## Decision 4: HTTP Status Codes

**Decision**: Follow the project's established README convention: all endpoints return `200 OK` on success, including `POST` and `DELETE`.

**Rationale**: The README table shows `200 OK` for all existing endpoints (including `POST /tickets` and `DELETE /tickets/:id`). The constitution states README templates are canonical. Although these are new endpoints without a README entry yet, deviating from the established project-wide convention would create inconsistency. The README will be updated with `200 OK` for all comment endpoints, making those templates canonical going forward.

**Alternatives considered**: Standard REST (`201 Created` for POST, `204 No Content` for DELETE). Rejected for consistency with the existing project API surface.

---

## Decision 5: Author Extraction

**Decision**: The controller receives `@AuthenticationPrincipal UserDetails principal` and passes `principal.getUsername()` to the service. The service loads the `User` from `UserRepository.findByUsername(username).orElseThrow(NotFoundException::new)`.

**Rationale**: The existing `IssueFlowUserDetailsService` loads by username, so username is the natural identifier available from the JWT context. No new security plumbing is required.

**Alternatives considered**: Pass user ID directly from the token claim. Rejected — requires examining and potentially modifying the JWT service; username lookup is simpler and consistent with existing auth patterns.

---

## Decision 6: Optimistic Locking

**Decision**: `@Version Long version` on Comment (same as Ticket). The `PATCH` request body includes `version`. The service performs a manual pre-check (`request.version() != comment.getVersion()` → throw `StaleVersionException`). JPA `@Version` acts as the final concurrency safety net.

**Rationale**: The manual pre-check gives an explicit, testable code path for the stale-version scenario. The JPA `@Version` backstop catches true concurrent conflicts that slip through between the load and the save. A new `StaleVersionException` (in `common/error/`) is needed because `InvalidStateTransitionException` is semantically reserved for status-machine transitions.

**Alternatives considered**: Rely entirely on JPA's `ObjectOptimisticLockingFailureException` without a manual pre-check. Rejected — makes the explicit `version` field in the request body meaningless and harder to unit-test.

---

## Decision 7: Ticket Existence Validation

**Decision**: Use `ticketRepository.findByIdAndDeletedAtIsNull(ticketId).orElseThrow(NotFoundException::new)` in the comment service.

**Rationale**: The user's input references `@SQLRestriction` on Ticket as the mechanism for automatic soft-delete filtering. However, the codebase currently uses `AndDeletedAtIsNull` repository method suffixes. Using the existing pattern is safe and consistent. If `@SQLRestriction("deleted_at IS NULL")` is added to the Ticket entity in a future cleanup, the service call can be simplified to `findById`, but adding `@SQLRestriction` is out of scope for this feature to avoid touching a shipped entity.

**Alternatives considered**: Add `@SQLRestriction` to Ticket entity now. Rejected — changes a shipped entity and its repository, expanding scope beyond comments.

---

## Decision 8: Field Name

**Decision**: The comment text field is named `content` in the entity and all DTOs.

**Rationale**: The user's implementation input explicitly specifies `content`. The spec used `body`, but implementation guidance takes precedence for code-level names.

---

## Decision 9: New Exception Class

**Decision**: Add `StaleVersionException` to `com.att.tdp.issueflow.common.error` and map it to `409 Conflict` in `GlobalExceptionHandler`.

**Rationale**: The version mismatch scenario deserves a named exception to keep the service code intention-revealing (Principle I) and to make unit tests explicit. Reusing `InvalidStateTransitionException` would be semantically wrong.
