package com.att.tdp.issueflow.common.error;

/**
 * Thrown when a requested ticket status transition is not allowed by the forward-only lifecycle
 * rule, or when a modification is attempted on a terminal (DONE) ticket. Mapped to {@code 409
 * Conflict} by {@link GlobalExceptionHandler}.
 */
public class InvalidStateTransitionException extends RuntimeException {

  /**
   * Constructs an exception with a human-readable reason describing the invalid transition.
   *
   * @param reason description of why the transition was rejected; safe to surface to clients
   */
  public InvalidStateTransitionException(String reason) {
    super(reason);
  }
}
