package com.att.tdp.issueflow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /projects}. {@code name} and {@code ownerId} are required; {@code
 * description} is optional.
 *
 * @param name project name — required, non-blank, max 255 characters
 * @param description optional free-text description; may be {@code null} or empty
 * @param ownerId identifier of the owning user — required; the service verifies the user exists
 */
public record CreateProjectRequest(
    @NotBlank @Size(max = 255) String name, String description, @NotNull Long ownerId) {}
