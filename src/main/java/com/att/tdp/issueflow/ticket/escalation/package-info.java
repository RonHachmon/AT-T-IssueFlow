/**
 * Auto-escalation sub-slice for tickets: a scheduled background task that bumps the priority of
 * overdue, non-{@code DONE} tickets up the {@code LOW → MEDIUM → HIGH → CRITICAL} ladder. Once a
 * ticket has reached {@code CRITICAL}, no further work is done — the {@code isOverdue} response
 * field already signals that the ticket is past its due date.
 */
package com.att.tdp.issueflow.ticket.escalation;
