package com.att.tdp.issueflow.comment.dto;

/**
 * Minimal projection of a mentioned user embedded in {@link CommentResponse#mentionedUsers}.
 * Carries just enough identity for clients to render the mention without leaking sensitive fields
 * such as email, role, or password hash.
 *
 * @param id the mentioned user's identifier
 * @param username the user's handle, original casing preserved
 * @param fullName the user's display name
 */
public record MentionedUserSummary(Long id, String username, String fullName) {}
