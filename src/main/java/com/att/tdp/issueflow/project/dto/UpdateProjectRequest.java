package com.att.tdp.issueflow.project.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /projects/{projectId}}. Both fields are optional individually, but
 * at least one MUST be non-null — the {@link #hasAtLeastOneFieldSet()} rule rejects empty requests.
 * Owner transfer is explicitly out of scope; {@code ownerId} is absent by design.
 *
 * @param name new project name (max 255 chars), or {@code null} to leave unchanged
 * @param description new description, or {@code null} to leave unchanged
 */
public record UpdateProjectRequest(@Size(max = 255) String name, String description) {

  /**
   * Bean Validation rule: at least one modifiable field must be supplied. Returning {@code false}
   * causes Spring to raise a 400 ProblemDetail naming this method.
   *
   * @return {@code true} when at least one of {@code name} or {@code description} is non-null
   */
  @AssertTrue(message = "at least one of name or description must be supplied")
  public boolean hasAtLeastOneFieldSet() {
    return name != null || description != null;
  }
}
