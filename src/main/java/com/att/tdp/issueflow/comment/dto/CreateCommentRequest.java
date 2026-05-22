package com.att.tdp.issueflow.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for posting a new comment.
 *
 * @param authorId the id of the user posting the comment
 * @param content the comment text; must be non-blank and at most 2000 characters
 */
public record CreateCommentRequest(
    @NotNull Long authorId, @NotBlank @Size(max = 2000) String content) {}
