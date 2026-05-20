package com.att.tdp.issueflow.common.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
}
