package com.att.tdp.issueflow.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.att.tdp.issueflow.user.Role;

/**
 * Request body for {@code POST /users}. All four fields are required; Bean Validation rejects blank
 * or out-of-range submissions before the controller method runs.
 *
 * @param username 3–50 chars, alphanumerics plus {@code .}, {@code _}, {@code -}
 * @param email well-formed email address, max 254 chars
 * @param fullName display name, 1–100 chars after trimming
 * @param role {@link Role#ADMIN} or {@link Role#DEVELOPER}
 */
public record CreateUserRequest(
    @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(
            regexp = "^[a-zA-Z0-9._-]+$",
            message = "must contain only letters, digits, '.', '_', or '-'")
        String username,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 1, max = 100) String fullName,
    @NotNull Role role) {}
