package com.att.tdp.issueflow.ticket;

/**
 * Lifecycle states a ticket can occupy. The ordinal order encodes the only valid progression:
 * {@code TODO → IN_PROGRESS → IN_REVIEW → DONE}. DONE is the terminal state — no further
 * transitions or field updates are accepted once a ticket reaches it.
 *
 * <p>Using ordinals for sequencing is intentional: the enum is sealed and the four-step workflow is
 * a fixed domain invariant. Any future extension requires an explicit migration review.
 */
public enum TicketStatus {
  TODO,
  IN_PROGRESS,
  IN_REVIEW,
  DONE;

  /**
   * Returns {@code true} when this status is the terminal state ({@code DONE}). A terminal ticket
   * cannot be updated or transitioned further.
   *
   * @return {@code true} if this status is {@code DONE}
   */
  public boolean isTerminal() {
    return this == DONE;
  }

  /**
   * Returns {@code true} when {@code target} is the immediate next step after this status in the
   * forward-only lifecycle. Skip-step and backward transitions both return {@code false}.
   *
   * @param target the requested target status
   * @return {@code true} if {@code target.ordinal() == this.ordinal() + 1}
   */
  public boolean isImmediateSuccessor(TicketStatus target) {
    return target.ordinal() == this.ordinal() + 1;
  }
}
