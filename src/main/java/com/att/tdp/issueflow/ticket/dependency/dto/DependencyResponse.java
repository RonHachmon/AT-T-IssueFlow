package com.att.tdp.issueflow.ticket.dependency.dto;

import com.att.tdp.issueflow.ticket.TicketStatus;

/**
 * Public representation of a blocker ticket returned by {@code GET
 * /tickets/{ticketId}/dependencies}. Only the minimum needed to render the dependency on the UI is
 * exposed.
 *
 * @param id the blocker ticket identifier
 * @param title the blocker ticket title
 * @param status the blocker ticket status
 */
public record DependencyResponse(Long id, String title, TicketStatus status) {}
