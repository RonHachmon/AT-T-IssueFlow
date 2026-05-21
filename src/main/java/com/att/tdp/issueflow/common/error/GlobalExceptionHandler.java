package com.att.tdp.issueflow.common.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Single source of truth for converting unhandled exceptions into RFC 7807 {@link ProblemDetail}
 * responses. Every controller in IssueFlow throws domain or framework exceptions; none catches
 * them. This advice maps them to a consistent {@code application/problem+json} envelope.
 *
 * <p>Reviewers MUST reject any controller that catches exceptions to convert them into HTTP
 * responses — that logic belongs here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * Maps Bean Validation failures on request bodies to a 422 problem with an {@code errors}
   * extension array listing the offending fields.
   *
   * @param exception the validation exception raised by Spring before controller invocation
   * @return a 422 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
    List<Map<String, String>> errors = new ArrayList<>();
    for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
      errors.add(
          Map.of(
              "field",
              fieldError.getField(),
              "message",
              fieldError.getDefaultMessage() == null ? "" : fieldError.getDefaultMessage()));
    }

    ProblemDetail problem =
        ProblemDetailFactory.validationFailed("One or more fields failed validation.");
    problem.setProperty("errors", errors);

    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
  }

  /**
   * Maps unparseable request bodies (malformed JSON, wrong types) to a 400 problem.
   *
   * @param exception the parser exception
   * @return a 400 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleMalformedBody(
      HttpMessageNotReadableException exception) {
    ProblemDetail problem =
        ProblemDetailFactory.malformedRequest("Request body could not be parsed.");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  /**
   * Maps requests to unknown routes to a 404 problem.
   *
   * @param exception the resource-resolution failure
   * @return a 404 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleRouteNotFound(NoResourceFoundException exception) {
    ProblemDetail problem =
        ProblemDetailFactory.notFound("No handler for " + exception.getResourcePath());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
  }

  /**
   * Maps service-thrown not-found exceptions to a 404 problem naming the missing resource and id.
   *
   * @param exception the not-found exception raised by a service
   * @return a 404 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException exception) {
    String detail = exception.getResource() + " " + exception.getResourceId() + " does not exist";
    ProblemDetail problem = ProblemDetailFactory.notFound(detail);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
  }

  /**
   * Maps service-thrown duplicate-resource exceptions to a 409 problem naming the offending field
   * and value.
   *
   * @param exception the duplicate-resource exception raised by a service
   * @return a 409 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(DuplicateResourceException.class)
  public ResponseEntity<ProblemDetail> handleDuplicateResource(
      DuplicateResourceException exception) {
    String detail = exception.getField() + " '" + exception.getValue() + "' is already in use";
    ProblemDetail problem = ProblemDetailFactory.duplicateResource(detail);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  /**
   * Race-condition safety net for unique-constraint violations that slip past application-level
   * pre-checks. Maps to a 409 with a generic message; the SQL fragment is logged but never surfaced
   * in the response body.
   *
   * @param exception the JPA/JDBC integrity violation
   * @return a 409 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
      DataIntegrityViolationException exception) {
    log.warn("Data integrity violation", exception);
    ProblemDetail problem =
        ProblemDetailFactory.duplicateResource("A unique field collides with an existing record.");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  /**
   * Maps Bean Validation failures on path variables or query parameters (raised by
   * {@code @Validated} on the controller class) to a 422 problem with an {@code errors} extension
   * array.
   *
   * @param exception the constraint-violation exception
   * @return a 422 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(
      ConstraintViolationException exception) {
    List<Map<String, String>> errors = new ArrayList<>();
    for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
      errors.add(
          Map.of(
              "field",
              lastPathNode(violation.getPropertyPath()),
              "message",
              violation.getMessage()));
    }

    ProblemDetail problem =
        ProblemDetailFactory.validationFailed("One or more parameters failed validation.");
    problem.setProperty("errors", errors);

    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
  }

  /**
   * Maps Spring Security authentication failures (bad credentials, disabled account, etc.) to a 401
   * problem. The detail is intentionally generic — never reveals whether the username or password
   * was wrong.
   *
   * @param exception the authentication failure thrown by the authentication manager
   * @return a 401 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ProblemDetail> handleAuthenticationFailure(
      AuthenticationException exception) {
    ProblemDetail problem = ProblemDetailFactory.unauthorized("Invalid username or password.");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
  }

  /**
   * Catch-all for any otherwise-unhandled exception. Logs the stack server-side and surfaces a
   * sanitized 500 problem; never leaks stack frames or secrets in the response body.
   *
   * @param exception the unexpected failure
   * @return a 500 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
    log.error("Unhandled exception", exception);
    ProblemDetail problem = ProblemDetailFactory.internalError("An unexpected error occurred.");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }

  private static String lastPathNode(Path path) {
    String result = "";
    for (Path.Node node : path) {
      result = node.getName();
    }
    return result;
  }
}
