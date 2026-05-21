package com.att.tdp.issueflow.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import com.att.tdp.issueflow.user.Role;

/**
 * Request body for {@code PATCH /users/{userId}}. Both fields are optional, but at least one MUST
 * be supplied — the {@link #hasAtLeastOneFieldSet()} method is a Bean Validation rule that rejects
 * empty requests with a 422.
 *
 * <p>Username and email are deliberately absent: they are immutable through this endpoint. A
 * request body that contains either is silently ignored by Jackson, and (with no other fields set)
 * the request fails the empty-rejection rule below.
 *
 * @param fullName new display name (1–100 chars), or {@code null} to leave unchanged
 * @param role new role, or {@code null} to leave unchanged
 */
public record UpdateUserRequest(@Size(min = 1, max = 100) String fullName, Role role) {

  /**
   * Bean Validation rule: at least one modifiable field must be supplied. Returning {@code false}
   * here causes Spring to raise a 422 ProblemDetail naming this method.
   *
   * @return {@code true} when at least one of {@code fullName} or {@code role} is non-null
   */
  @AssertTrue(message = "at least one of fullName or role must be supplied")
  public boolean hasAtLeastOneFieldSet() {
    return fullName != null || role != null;
  }
}
