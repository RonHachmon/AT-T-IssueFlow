package com.att.tdp.issueflow.user.dto;

import com.att.tdp.issueflow.user.Role;

/**
 * Response body for every user-returning endpoint. Shape matches the README's Users APIs table
 * exactly — five fields, no timestamps. The entity still carries {@code createdAt}/{@code
 * updatedAt} for audit purposes; they are simply not surfaced to clients.
 *
 * <p>{@code passwordHash} is deliberately absent. MapStruct's {@code unmappedTargetPolicy=ERROR}
 * guarantees a compile failure if a new sensitive entity field is silently included here.
 *
 * @param id server-assigned identifier
 * @param username unique short handle (case-insensitive, original casing preserved)
 * @param email unique email address (case-insensitive, original casing preserved)
 * @param fullName display name
 * @param role {@link Role#ADMIN} or {@link Role#DEVELOPER}
 */
public record UserResponse(Long id, String username, String email, String fullName, Role role) {}
