package com.att.tdp.issueflow.common.error;

/**
 * Thrown when an authenticated caller attempts an action they are not permitted to perform. The
 * {@link GlobalExceptionHandler} maps this to {@code 403 Forbidden} with an RFC 7807 ProblemDetail.
 */
public class ForbiddenException extends RuntimeException {

  /**
   * Constructs a forbidden exception with a human-readable reason.
   *
   * @param message explanation safe to surface in the API response
   */
  public ForbiddenException(String message) {
    super(message);
  }
}
