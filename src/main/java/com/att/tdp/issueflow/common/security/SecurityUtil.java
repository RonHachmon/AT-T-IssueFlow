package com.att.tdp.issueflow.common.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Static helper for reading the authenticated principal from the current thread's security context.
 */
public final class SecurityUtil {

  private SecurityUtil() {}

  /**
   * Returns the username of the currently authenticated principal, or empty when the request is
   * unauthenticated, anonymous, or the security context has not been populated.
   *
   * @return the username, or {@link Optional#empty()} for unauthenticated / anonymous calls
   */
  public static Optional<String> currentUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return Optional.empty();
    }
    Object principal = auth.getPrincipal();
    if ("anonymousUser".equals(principal)) {
      return Optional.empty();
    }
    if (principal instanceof UserDetails ud) {
      return Optional.of(ud.getUsername());
    }
    if (principal instanceof String s) {
      return Optional.of(s);
    }
    return Optional.empty();
  }
}
