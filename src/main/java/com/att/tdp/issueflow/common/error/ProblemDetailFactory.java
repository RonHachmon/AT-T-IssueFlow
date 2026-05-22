package com.att.tdp.issueflow.common.error;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds {@link ProblemDetail} responses with the canonical IssueFlow shape: a stable {@code type}
 * URI from {@link ErrorType}, an HTTP status, a short {@code title}, and a per-occurrence {@code
 * detail}.
 *
 * <p>Centralizing construction here keeps every handler in {@link GlobalExceptionHandler} short and
 * ensures the wire shape stays consistent across error classes.
 */
public final class ProblemDetailFactory {

  private ProblemDetailFactory() {
    // utility class — not instantiable
  }

  /**
   * Builds a 404 problem.
   *
   * @param detail per-occurrence explanation, safe to surface to clients
   * @return a populated {@link ProblemDetail}
   */
  public static ProblemDetail notFound(String detail) {
    return build(HttpStatus.NOT_FOUND, ErrorType.NOT_FOUND, "Resource not found", detail);
  }

  /**
   * Builds a 422 problem for semantic validation failures.
   *
   * @param detail per-occurrence explanation
   * @return a populated {@link ProblemDetail}
   */
  public static ProblemDetail validationFailed(String detail) {
    return build(
        HttpStatus.UNPROCESSABLE_ENTITY, ErrorType.VALIDATION_FAILED, "Validation failed", detail);
  }

  /**
   * Builds a 400 problem for malformed requests (unparseable JSON, wrong types).
   *
   * @param detail per-occurrence explanation
   * @return a populated {@link ProblemDetail}
   */
  public static ProblemDetail malformedRequest(String detail) {
    return build(HttpStatus.BAD_REQUEST, ErrorType.MALFORMED_REQUEST, "Malformed request", detail);
  }

  /**
   * Builds a 409 problem for state conflicts (illegal transitions, etc.).
   *
   * @param detail per-occurrence explanation
   * @return a populated {@link ProblemDetail}
   */
  public static ProblemDetail conflict(String detail) {
    return build(HttpStatus.CONFLICT, ErrorType.CONFLICT, "Conflict", detail);
  }

  /**
   * Builds a 409 problem for duplicate-resource collisions (unique field already in use).
   *
   * @param detail per-occurrence explanation; SHOULD name the offending field and value
   * @return a populated {@link ProblemDetail}
   */
  public static ProblemDetail duplicateResource(String detail) {
    return build(HttpStatus.CONFLICT, ErrorType.DUPLICATE_RESOURCE, "Duplicate resource", detail);
  }

  /**
   * Builds a 401 problem for authentication failures (bad credentials, expired token, etc.). The
   * detail is intentionally generic — never reveals whether the username or password was wrong.
   *
   * @param detail per-occurrence explanation, safe to surface to clients
   * @return a populated {@link ProblemDetail}
   */
  public static ProblemDetail unauthorized(String detail) {
    return build(HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED, "Unauthorized", detail);
  }

  /**
   * Builds a 403 problem for authorization failures (authenticated caller lacks permission).
   *
   * @param detail per-occurrence explanation, safe to surface to clients
   * @return a populated {@link ProblemDetail}
   */
  public static ProblemDetail forbidden(String detail) {
    return build(HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN, "Forbidden", detail);
  }

  /**
   * Builds a 413 problem for file uploads that exceed the maximum allowed size.
   *
   * @param detail per-occurrence explanation, safe to surface to clients
   * @return a populated {@link ProblemDetail}
   */
  public static ProblemDetail fileTooLarge(String detail) {
    return build(HttpStatus.PAYLOAD_TOO_LARGE, ErrorType.FILE_TOO_LARGE, "File too large", detail);
  }

  /**
   * Builds a 415 problem for file uploads whose MIME type is not permitted.
   *
   * @param detail per-occurrence explanation, safe to surface to clients
   * @return a populated {@link ProblemDetail}
   */
  public static ProblemDetail unsupportedFileType(String detail) {
    return build(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        ErrorType.UNSUPPORTED_FILE_TYPE,
        "Unsupported file type",
        detail);
  }

  /**
   * Builds a 500 problem as a generic fallback.
   *
   * @param detail per-occurrence explanation; MUST NOT contain stack traces or secrets
   * @return a populated {@link ProblemDetail}
   */
  public static ProblemDetail internalError(String detail) {
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorType.INTERNAL_ERROR,
        "Internal server error",
        detail);
  }

  private static ProblemDetail build(HttpStatus status, String type, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setType(URI.create(type));
    problem.setTitle(title);
    problem.setDetail(detail);
    return problem;
  }
}
