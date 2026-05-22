package com.att.tdp.issueflow.ticket.dependency;

import org.mapstruct.Mapper;

import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.dependency.dto.DependencyResponse;

/**
 * Compile-time mapping between a blocker {@link Ticket} and the minimal {@link DependencyResponse}
 * shape returned by {@code GET /tickets/{ticketId}/dependencies}.
 */
@Mapper
public interface TicketDependencyMapper {

  /**
   * Projects a blocker {@link Ticket} to a {@link DependencyResponse} containing only id, title,
   * and status.
   *
   * @param blocker the blocker ticket
   * @return a response DTO with the three fields visible on the dependencies list
   */
  DependencyResponse toResponse(Ticket blocker);
}
