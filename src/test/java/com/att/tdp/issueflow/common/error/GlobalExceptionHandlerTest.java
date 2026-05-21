package com.att.tdp.issueflow.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void returnsValidationFailedProblemWithFieldErrorsWhenMethodArgumentInvalid() {
    BindingResult bindingResult = mock(BindingResult.class);
    when(bindingResult.getFieldErrors())
        .thenReturn(
            List.of(
                new FieldError("ticket", "title", "must not be blank"),
                new FieldError("ticket", "priority", null)));
    MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
    when(exception.getBindingResult()).thenReturn(bindingResult);

    ResponseEntity<ProblemDetail> response = handler.handleValidation(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    ProblemDetail problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(422);
    assertThat(problem.getType().toString()).isEqualTo(ErrorType.VALIDATION_FAILED);
    assertThat(problem.getTitle()).isEqualTo("Validation failed");
    @SuppressWarnings("unchecked")
    List<Map<String, String>> errors =
        (List<Map<String, String>>) problem.getProperties().get("errors");
    assertThat(errors).hasSize(2);
    assertThat(errors.get(0))
        .containsEntry("field", "title")
        .containsEntry("message", "must not be blank");
    assertThat(errors.get(1)).containsEntry("field", "priority").containsEntry("message", "");
  }

  @Test
  void returnsMalformedRequestProblemWhenBodyCannotBeParsed() {
    HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);

    ResponseEntity<ProblemDetail> response = handler.handleMalformedBody(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    ProblemDetail problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getType().toString()).isEqualTo(ErrorType.MALFORMED_REQUEST);
    assertThat(problem.getTitle()).isEqualTo("Malformed request");
  }

  @Test
  void returnsNotFoundProblemWhenRouteUnknown() {
    NoResourceFoundException exception = mock(NoResourceFoundException.class);
    when(exception.getResourcePath()).thenReturn("api/unknown");

    ResponseEntity<ProblemDetail> response = handler.handleRouteNotFound(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    ProblemDetail problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(404);
    assertThat(problem.getType().toString()).isEqualTo(ErrorType.NOT_FOUND);
    assertThat(problem.getDetail()).contains("api/unknown");
  }

  @Test
  void returnsUnauthorizedProblemWhenCredentialsAreInvalid() {
    BadCredentialsException exception = new BadCredentialsException("Bad credentials");

    ResponseEntity<ProblemDetail> response = handler.handleAuthenticationFailure(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    ProblemDetail problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(401);
    assertThat(problem.getType().toString()).isEqualTo(ErrorType.UNAUTHORIZED);
    assertThat(problem.getTitle()).isEqualTo("Unauthorized");
    assertThat(problem.getDetail()).doesNotContain("Bad credentials");
  }

  @Test
  void returnsInternalErrorProblemForUnexpectedException() {
    Exception exception = new RuntimeException("database melted");

    ResponseEntity<ProblemDetail> response = handler.handleUnexpected(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    ProblemDetail problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getType().toString()).isEqualTo(ErrorType.INTERNAL_ERROR);
    assertThat(problem.getDetail()).doesNotContain("database melted");
  }

  @Test
  void returnsNotFoundProblemForNotFoundException() {
    NotFoundException exception = new NotFoundException("User", 42L);

    ResponseEntity<ProblemDetail> response = handler.handleNotFound(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    ProblemDetail problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(404);
    assertThat(problem.getType().toString()).isEqualTo(ErrorType.NOT_FOUND);
    assertThat(problem.getDetail()).contains("User", "42");
  }

  @Test
  void returnsConflictProblemForDuplicateResourceException() {
    DuplicateResourceException exception = new DuplicateResourceException("username", "alice");

    ResponseEntity<ProblemDetail> response = handler.handleDuplicateResource(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    ProblemDetail problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getType().toString()).isEqualTo(ErrorType.DUPLICATE_RESOURCE);
    assertThat(problem.getDetail()).contains("username", "alice");
  }

  @Test
  void returnsConflictProblemForDataIntegrityViolationException() {
    DataIntegrityViolationException exception =
        new DataIntegrityViolationException(
            "ERROR: duplicate key value violates unique constraint");

    ResponseEntity<ProblemDetail> response = handler.handleDataIntegrityViolation(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    ProblemDetail problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getType().toString()).isEqualTo(ErrorType.DUPLICATE_RESOURCE);
    assertThat(problem.getDetail()).doesNotContain("ERROR:", "unique constraint");
  }

  @Test
  void returnsValidationFailedProblemForConstraintViolationException() {
    Path path = mock(Path.class);
    Path.Node node = mock(Path.Node.class);
    when(node.getName()).thenReturn("pageSize");
    when(path.iterator()).thenReturn(List.of(node).iterator());

    ConstraintViolation<?> violation = mock(ConstraintViolation.class);
    when(violation.getPropertyPath()).thenReturn(path);
    when(violation.getMessage()).thenReturn("must be less than or equal to 100");

    ConstraintViolationException exception =
        new ConstraintViolationException("constraint", Set.of(violation));

    ResponseEntity<ProblemDetail> response = handler.handleConstraintViolation(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    ProblemDetail problem = response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(422);
    assertThat(problem.getType().toString()).isEqualTo(ErrorType.VALIDATION_FAILED);
    @SuppressWarnings("unchecked")
    List<Map<String, String>> errors =
        (List<Map<String, String>>) problem.getProperties().get("errors");
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0))
        .containsEntry("field", "pageSize")
        .containsEntry("message", "must be less than or equal to 100");
  }
}
