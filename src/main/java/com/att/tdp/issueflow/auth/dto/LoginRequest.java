package com.att.tdp.issueflow.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credentials submitted by a caller to obtain a JWT.
 *
 * @param username the caller's username (must not be blank)
 * @param password the caller's password (must not be blank)
 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
