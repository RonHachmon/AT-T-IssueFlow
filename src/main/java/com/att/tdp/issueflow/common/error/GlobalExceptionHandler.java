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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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
   * Maps method security authorization failures (e.g., failed @PreAuthorize validation checks) to
   * an RFC 7807 403 Forbidden problem detail.
   *
   * @param exception the method security check failure exception
   * @return a 403 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(AuthorizationDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAuthorizationDenied(
      AuthorizationDeniedException exception) {
    ProblemDetail problem = ProblemDetailFactory.forbidden("Access Denied");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
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
   * Maps authorization failures (authenticated caller not permitted to act) to a 403 problem.
   *
   * @param exception the forbidden exception raised by a service
   * @return a 403 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ProblemDetail> handleForbidden(ForbiddenException exception) {
    ProblemDetail problem = ProblemDetailFactory.forbidden(exception.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }

  /**
   * Maps invalid ticket-state transition attempts and modifications to frozen DONE tickets to a 409
   * problem. The exception message is surfaced directly as the detail — it is always intentionally
   * human-readable.
   *
   * @param exception the invalid-transition exception raised by the ticket service
   * @return a 409 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(InvalidStateTransitionException.class)
  public ResponseEntity<ProblemDetail> handleInvalidTransition(
      InvalidStateTransitionException exception) {
    ProblemDetail problem = ProblemDetailFactory.conflict(exception.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  /**
   * Maps stale optimistic-lock version mismatches to a 409 problem. Clients must re-fetch the
   * resource and retry with the latest version.
   *
   * @param exception the stale-version exception raised by the comment service
   * @return a 409 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(StaleVersionException.class)
  public ResponseEntity<ProblemDetail> handleStaleVersion(StaleVersionException exception) {
    ProblemDetail problem = ProblemDetailFactory.conflict(exception.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  /**
   * Maps JPA optimistic-lock failures (concurrent writes to the same ticket version) to a 409
   * problem. Clients should fetch the latest version and retry.
   *
   * @param exception the optimistic-lock failure raised by JPA
   * @return a 409 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleOptimisticLock(
      ObjectOptimisticLockingFailureException exception) {
    ProblemDetail problem =
        ProblemDetailFactory.conflict(
            "Ticket was modified concurrently. Fetch the latest version and retry.");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  /**
   * Maps an invalid filter combination (entityId without entityType) to a 400 problem.
   *
   * @param exception the filter-validation exception raised by AuditLogService
   * @return a 400 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(InvalidFilterException.class)
  public ResponseEntity<ProblemDetail> handleInvalidFilter(InvalidFilterException exception) {
    ProblemDetail problem = ProblemDetailFactory.malformedRequest(exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  /**
   * Maps query-parameter type mismatches (e.g. unknown enum value) to a 400 problem.
   *
   * @param exception the type-mismatch exception raised by Spring's data binding
   * @return a 400 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleTypeMismatch(
      MethodArgumentTypeMismatchException exception) {
    String detail =
        "Invalid value '" + exception.getValue() + "' for parameter '" + exception.getName() + "'";
    ProblemDetail problem = ProblemDetailFactory.malformedRequest(detail);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  /**
   * Maps service-layer file-too-large rejections to a 413 problem.
   *
   * @param exception the file-too-large exception raised by the attachment service
   * @return a 413 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(FileTooLargeException.class)
  public ResponseEntity<ProblemDetail> handleFileTooLarge(FileTooLargeException exception) {
    ProblemDetail problem = ProblemDetailFactory.fileTooLarge(exception.getMessage());
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem);
  }

  /**
   * Maps Spring's multipart size guard to a 413 problem. Fired before the controller is reached
   * when the request body exceeds the configured {@code max-file-size} or {@code max-request-size}.
   *
   * @param exception the multipart-size-exceeded exception raised by Spring
   * @return a 413 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ProblemDetail> handleMaxUploadSizeExceeded(
      MaxUploadSizeExceededException exception) {
    ProblemDetail problem =
        ProblemDetailFactory.fileTooLarge("Upload exceeds the server's maximum allowed size.");
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem);
  }

  /**
   * Maps unsupported MIME type rejections (declared or Tika-detected) to a 415 problem.
   *
   * @param exception the unsupported-file-type exception raised by the attachment service
   * @return a 415 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(UnsupportedFileTypeException.class)
  public ResponseEntity<ProblemDetail> handleUnsupportedFileType(
      UnsupportedFileTypeException exception) {
    ProblemDetail problem = ProblemDetailFactory.unsupportedFileType(exception.getMessage());
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problem);
  }

  /**
   * Maps Spring's dispatcher-level Content-Type rejection (request Content-Type does not match any
   * handler's {@code consumes}) to a 415 problem. Without this, the exception falls through to the
   * catch-all 500 handler.
   *
   * @param exception the media-type-not-supported exception raised by Spring's dispatcher
   * @return a 415 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException exception) {
    String detail =
        exception.getContentType() != null
            ? "Request Content-Type '" + exception.getContentType() + "' is not supported."
            : "Request Content-Type is not supported.";
    ProblemDetail problem = ProblemDetailFactory.unsupportedFileType(detail);
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problem);
  }

  /**
   * Maps a multipart-required handler being hit by a non-multipart request to a 400 problem. Spring
   * raises this when {@code @RequestParam MultipartFile} is bound but the request body is not
   * {@code multipart/form-data}.
   *
   * <p>Declared more specifically than {@link MaxUploadSizeExceededException} (which extends {@link
   * MultipartException}); the more-specific handler wins by Spring's resolution rules, so 413
   * oversize-upload responses are unaffected.
   *
   * @param exception the multipart-parsing exception raised by Spring's argument resolver
   * @return a 400 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<ProblemDetail> handleMultipart(MultipartException exception) {
    ProblemDetail problem =
        ProblemDetailFactory.malformedRequest(
            "Request must be multipart/form-data with a 'file' part.");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  /**
   * Maps a valid multipart request that omits the required {@code file} part to a 400 problem.
   *
   * @param exception the missing-part exception raised by Spring's argument resolver
   * @return a 400 ProblemDetail wrapped in a {@link ResponseEntity}
   */
  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ProblemDetail> handleMissingPart(
      MissingServletRequestPartException exception) {
    String detail = "Required multipart part '" + exception.getRequestPartName() + "' is missing.";
    ProblemDetail problem = ProblemDetailFactory.malformedRequest(detail);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
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
