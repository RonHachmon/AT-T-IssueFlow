package com.att.tdp.issueflow.ticket;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;

/**
 * REST controller for the tickets surface. The endpoint contract is fixed by the project's {@code
 * README.md} Tickets APIs table — every verb, path, and status code matches that table one-to-one.
 * All five endpoints return {@code 200 OK} per the README canonical template. Validation triggering
 * lives here; business logic lives in {@link TicketService}.
 */
@RestController
@RequestMapping("/tickets")
public class TicketController {

  private final TicketService ticketService;

  public TicketController(TicketService ticketService) {
    this.ticketService = ticketService;
  }

  /**
   * Creates a new ticket with status {@code TODO}.
   *
   * @param request the validated request body
   * @return {@code 200 OK} with the persisted ticket
   */
  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  public TicketResponse createTicket(@Valid @RequestBody CreateTicketRequest request) {
    return ticketService.create(request);
  }

  /**
   * Returns all active tickets belonging to the given project as a plain JSON array. Soft-deleted
   * tickets and tickets belonging to a soft-deleted project are excluded.
   *
   * @param projectId the owning project identifier
   * @return {@code 200 OK} with all active tickets for the project
   */
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public List<TicketResponse> listByProject(@RequestParam Long projectId) {
    return ticketService.listByProject(projectId);
  }

  /**
   * Fetches a single active ticket by id.
   *
   * @param ticketId the ticket identifier
   * @return {@code 200 OK} with the ticket; {@code 404} via global advice if unknown or deleted
   */
  @GetMapping("/{ticketId}")
  @ResponseStatus(HttpStatus.OK)
  public TicketResponse getById(@PathVariable Long ticketId) {
    return ticketService.getById(ticketId);
  }

  /**
   * Partially updates a ticket's fields and/or advances its status. Returns {@code 200 OK} with no
   * body. Attempting to modify a {@code DONE} ticket, or requesting an invalid status transition,
   * returns {@code 409} via global advice.
   *
   * @param ticketId the ticket identifier
   * @param request the validated partial update
   */
  @PatchMapping("/{ticketId}")
  @ResponseStatus(HttpStatus.OK)
  public void update(@PathVariable Long ticketId, @Valid @RequestBody UpdateTicketRequest request) {
    ticketService.update(ticketId, request);
  }

  /**
   * Soft-deletes a ticket. Returns {@code 200 OK} with no body. A second call with the same id
   * returns {@code 404} because the ticket is no longer visible as active.
   *
   * @param ticketId the ticket identifier
   */
  @DeleteMapping("/{ticketId}")
  @ResponseStatus(HttpStatus.OK)
  public void softDelete(@PathVariable Long ticketId) {
    ticketService.softDelete(ticketId);
  }
}
