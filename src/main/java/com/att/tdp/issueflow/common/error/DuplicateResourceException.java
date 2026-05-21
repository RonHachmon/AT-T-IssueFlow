package com.att.tdp.issueflow.common.error;

/**
 * Thrown by service methods when a uniqueness pre-check or a database constraint violation reveals
 * that the value supplied for a unique field is already in use. The {@link GlobalExceptionHandler}
 * maps this to {@code 409 Conflict} with an RFC 7807 ProblemDetail naming the offending field.
 */
public class DuplicateResourceException extends RuntimeException {

  private final String field;
  private final String value;

  /**
   * Constructs a duplicate-resource exception for a specific field and the value that collided.
   *
   * @param field name of the duplicated field, e.g. {@code "username"}
   * @param value the value that was already taken (safe to surface; MUST NOT carry secrets)
   */
  public DuplicateResourceException(String field, String value) {
    super(field + " '" + value + "' is already in use");
    this.field = field;
    this.value = value;
  }

  /**
   * @return the name of the duplicated field
   */
  public String getField() {
    return field;
  }

  /**
   * @return the value that was already taken
   */
  public String getValue() {
    return value;
  }
}
