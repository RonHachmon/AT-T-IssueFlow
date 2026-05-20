package com.att.tdp.issueflow.common.error;

/**
 * Stable RFC 7807 {@code type} URIs identifying each class of error returned by the IssueFlow API.
 *
 * <p>These URIs are the canonical identifier clients should key off; humans should read {@code
 * title} and {@code detail} from the {@link org.springframework.http.ProblemDetail}. The URIs do
 * not need to resolve — per RFC 7807 §3.1 they only need to be stable identifiers.
 */
public final class ErrorType {

  /** 404 — the requested resource does not exist. */
  public static final String NOT_FOUND = "https://issueflow.att.com/problems/not-found";

  /** 422 — request was well-formed but failed semantic validation. */
  public static final String VALIDATION_FAILED =
      "https://issueflow.att.com/problems/validation-failed";

  /** 400 — request could not be parsed (malformed JSON, wrong types, etc.). */
  public static final String MALFORMED_REQUEST =
      "https://issueflow.att.com/problems/malformed-request";

  /** 409 — conflict with current state (duplicate resource, illegal state transition). */
  public static final String CONFLICT = "https://issueflow.att.com/problems/conflict";

  /** 500 — generic fallback for unhandled internal failures. */
  public static final String INTERNAL_ERROR = "https://issueflow.att.com/problems/internal-error";

  private ErrorType() {
    // utility class — not instantiable
  }
}
