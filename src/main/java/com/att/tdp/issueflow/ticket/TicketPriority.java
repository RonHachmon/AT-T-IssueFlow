package com.att.tdp.issueflow.ticket;

/**
 * Urgency levels assignable to a ticket. No ordering semantics — the four values are treated as
 * independent labels by the domain; sorting or filtering by priority is left to future phases.
 */
public enum TicketPriority {
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}
