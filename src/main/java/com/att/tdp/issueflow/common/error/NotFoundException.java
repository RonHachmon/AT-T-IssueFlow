package com.att.tdp.issueflow.common.error;

/**
 * Thrown by service methods when a lookup by identifier finds nothing. The {@link
 * GlobalExceptionHandler} maps this to {@code 404 Not Found} with an RFC 7807 ProblemDetail naming
 * the missing resource and id.
 */
public class NotFoundException extends RuntimeException {

  private final String resource;
  private final Long id;

  /**
   * Constructs a not-found exception for a specific resource and id.
   *
   * @param resource short name of the resource type, e.g. {@code "User"}
   * @param id the identifier that was not found
   */
  public NotFoundException(String resource, Long id) {
    super(resource + " " + id + " does not exist");
    this.resource = resource;
    this.id = id;
  }

  /**
   * @return short name of the resource type, e.g. {@code "User"}
   */
  public String getResource() {
    return resource;
  }

  /**
   * @return the identifier that was not found
   */
  public Long getResourceId() {
    return id;
  }
}
