package com.att.tdp.issueflow.common.health;

import java.time.Instant;
import java.util.Map;

/**
 * Response body for {@code GET /health}. Reports the aggregated application status, the status of
 * each tracked component (application, db), and the time the sample was taken.
 *
 * @param status overall status, either {@code "UP"} or {@code "DOWN"}
 * @param components per-component status keyed by component name (always contains {@code
 *     "application"} and {@code "db"})
 * @param timestamp ISO-8601 UTC instant the health was sampled
 */
public record HealthResponse(
    String status, Map<String, ComponentStatus> components, Instant timestamp) {

  /**
   * Per-component health snapshot.
   *
   * @param status either {@code "UP"} or {@code "DOWN"}
   * @param detail empty when healthy; otherwise a short human-readable hint (no stack traces, no
   *     secrets)
   */
  public record ComponentStatus(String status, String detail) {}
}
