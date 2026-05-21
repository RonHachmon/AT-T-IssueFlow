package com.att.tdp.issueflow.ticket;

/**
 * Classification of the work a ticket represents. Used for reporting and filtering; carries no
 * behavioural difference in this phase.
 */
public enum TicketType {
  BUG,
  FEATURE,
  TECHNICAL
}
