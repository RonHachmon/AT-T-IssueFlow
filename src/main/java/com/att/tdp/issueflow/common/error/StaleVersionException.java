package com.att.tdp.issueflow.common.error;

/**
 * Thrown by {@link com.att.tdp.issueflow.comment.CommentService} when the caller supplies a {@code
 * version} that does not match the stored value, indicating the resource was modified since the
 * client last fetched it. Mapped to {@code 409 Conflict} by {@link GlobalExceptionHandler}.
 */
public class StaleVersionException extends RuntimeException {

  /**
   * Constructs the exception with a fixed, client-safe message instructing the caller to re-fetch
   * before retrying.
   */
  public StaleVersionException() {
    super("Resource has been modified by another request; re-fetch and retry.");
  }
}
