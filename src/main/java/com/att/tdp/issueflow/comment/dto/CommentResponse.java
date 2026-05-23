package com.att.tdp.issueflow.comment.dto;

import java.util.List;

/**
 * Public representation of a {@link com.att.tdp.issueflow.comment.Comment} returned by every
 * comment endpoint. Foreign-key associations are flattened to their id; {@code mentionedUsers}
 * contains the resolved users referenced by {@code @username} handles in {@code content}, in order
 * of first appearance.
 *
 * @param id the comment identifier
 * @param ticketId the id of the owning ticket
 * @param authorId the id of the user who posted the comment
 * @param content the comment text
 * @param mentionedUsers users mentioned via {@code @username} in {@code content}, ordered by first
 *     appearance; empty when the content has no mentions or only unknown handles
 */
public record CommentResponse(
    Long id,
    Long ticketId,
    Long authorId,
    String content,
    List<MentionedUserSummary> mentionedUsers) {}
