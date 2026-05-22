package com.att.tdp.issueflow.ticket.dependency;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.ticket.dependency.dto.AddDependencyRequest;
import com.att.tdp.issueflow.ticket.dependency.dto.DependencyResponse;

/**
 * REST controller for ticket-blocks-ticket dependencies. All endpoints are nested under {@code
 * /tickets/{ticketId}/dependencies}. Every success response is {@code 200 OK} per the README Ticket
 * Dependencies table — POST and DELETE return no body. Validation triggering lives here; business
 * logic lives in {@link TicketDependencyService}.
 */
@RestController
@RequestMapping("/tickets/{ticketId}/dependencies")
public class TicketDependencyController {

  private final TicketDependencyService dependencyService;

  public TicketDependencyController(TicketDependencyService dependencyService) {
    this.dependencyService = dependencyService;
  }

  /**
   * Adds a blocker dependency to the given ticket.
   *
   * @param ticketId the blocked ticket id (from the URL path)
   * @param request the validated body containing {@code blockedBy}, the blocker ticket id
   */
  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  public void addDependency(
      @PathVariable Long ticketId, @Valid @RequestBody AddDependencyRequest request) {
    dependencyService.addDependency(ticketId, request.blockedBy());
  }

  /**
   * Lists the active blockers for the given ticket, ordered by blocker ticket id ascending.
   *
   * @param ticketId the blocked ticket id (from the URL path)
   * @return active blockers as a plain array
   */
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public List<DependencyResponse> listDependencies(@PathVariable Long ticketId) {
    return dependencyService.listDependencies(ticketId);
  }

  /**
   * Removes a blocker dependency from the given ticket. Returns {@code 200 OK} with no body. A
   * second call with the same blocker id returns {@code 404}.
   *
   * @param ticketId the blocked ticket id (from the URL path)
   * @param blockerId the blocker ticket id to remove (from the URL path)
   */
  @DeleteMapping("/{blockerId}")
  @ResponseStatus(HttpStatus.OK)
  public void removeDependency(@PathVariable Long ticketId, @PathVariable Long blockerId) {
    dependencyService.removeDependency(ticketId, blockerId);
  }
}
