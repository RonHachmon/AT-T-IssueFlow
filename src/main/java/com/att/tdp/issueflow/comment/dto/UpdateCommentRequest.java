package com.att.tdp.issueflow.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for editing an existing comment.
 *
 * @param content the replacement text; must be non-blank and at most 2000 characters
 */
public record UpdateCommentRequest(@NotBlank @Size(max = 2000) String content) {}
