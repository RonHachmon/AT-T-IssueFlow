package com.att.tdp.issueflow.ticket;

/**
 * Urgency levels assignable to a ticket, ordered from least to most urgent. The declared order is
 * load-bearing: the auto-escalation scheduler walks the ladder via {@link #next()} when an overdue,
 * non-{@code DONE} ticket is encountered, stopping at {@link #CRITICAL}.
 */
public enum TicketPriority {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL;

  /**
   * Returns the next, strictly higher urgency level, or {@code null} when this value is already the
   * maximum.
   *
   * @return the immediate successor in escalation order, or {@code null} for {@link #CRITICAL}
   */
  public TicketPriority next() {
    TicketPriority[] values = values();
    return ordinal() == values.length - 1 ? null : values[ordinal() + 1];
  }

  /**
   * Whether this value is the maximum urgency level, beyond which the auto-escalation scheduler
   * flags the ticket instead of bumping further.
   *
   * @return {@code true} for {@link #CRITICAL}, {@code false} otherwise
   */
  public boolean isMax() {
    return this == CRITICAL;
  }
}
