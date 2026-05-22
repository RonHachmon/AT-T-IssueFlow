package com.att.tdp.issueflow.comment.dto;

import java.time.Instant;
import java.util.List;

/**
 * Public representation of a {@link com.att.tdp.issueflow.comment.Comment} returned by every
 * comment endpoint. Foreign-key associations are flattened to their id. {@code mentionedUsers} is
 * reserved for the future Mentions feature and always returned as an empty list.
 *
 * @param id the comment identifier
 * @param ticketId the id of the owning ticket
 * @param authorId the id of the user who posted the comment
 * @param content the comment text
 * @param createdAt when the comment was created (UTC)
 * @param updatedAt when the comment was last modified (UTC)
 * @param version the optimistic-lock counter
 * @param mentionedUsers placeholder for future @-mention resolution; always empty
 */
public record CommentResponse(
    Long id,
    Long ticketId,
    Long authorId,
    String content,
    Instant createdAt,
    Instant updatedAt,
    Long version,
    List<Object> mentionedUsers) {}
