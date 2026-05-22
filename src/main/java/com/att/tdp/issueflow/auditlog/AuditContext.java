package com.att.tdp.issueflow.auditlog;

/**
 * Thread-local hint that lets a service method communicate a semantic {@link AuditAction} to the
 * JPA entity listener without changing method signatures.
 *
 * <p>Usage: call {@link #hint(AuditAction)} immediately before {@code repository.save(...)}. The
 * hint is one-shot — {@link #consumeOrDefault(AuditAction)} reads and clears it in a single atomic
 * step so it cannot leak into a subsequent save on the same thread.
 */
public final class AuditContext {

  private static final ThreadLocal<AuditAction> HINT = new ThreadLocal<>();

  private AuditContext() {}

  /** Sets a one-shot hint for the next listener callback on this thread. */
  public static void hint(AuditAction action) {
    HINT.set(action);
  }

  /**
   * Returns the current hint and clears it, or returns {@code fallback} when no hint was set.
   *
   * @param fallback the default action when no hint is present
   * @return the hinted action, or {@code fallback}
   */
  public static AuditAction consumeOrDefault(AuditAction fallback) {
    AuditAction hint = HINT.get();
    HINT.remove();
    return hint != null ? hint : fallback;
  }
}
