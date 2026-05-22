package com.att.tdp.issueflow.ticket.dependency;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.common.error.DuplicateResourceException;
import com.att.tdp.issueflow.common.error.InvalidStateTransitionException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.dependency.dto.DependencyResponse;

/**
 * Business logic for ticket-blocks-ticket dependencies. The service throws semantic exceptions
 * ({@link NotFoundException}, {@link InvalidStateTransitionException}, {@link
 * DuplicateResourceException}) that the {@code @RestControllerAdvice} maps to HTTP status codes.
 *
 * <p>Add-dependency check order is significant — each early-return surfaces the most specific
 * error: self-dependency, missing/soft-deleted tickets, cross-project blocker, duplicate row,
 * cycle. The cycle check is the most expensive (Java-side DFS over the existing edges) and is run
 * last.
 */
@Service
public class TicketDependencyService {

  private static final String RESOURCE = "Ticket";

  private final TicketDependencyRepository dependencyRepository;
  private final TicketRepository ticketRepository;
  private final TicketDependencyMapper dependencyMapper;

  public TicketDependencyService(
      TicketDependencyRepository dependencyRepository,
      TicketRepository ticketRepository,
      TicketDependencyMapper dependencyMapper) {
    this.dependencyRepository = dependencyRepository;
    this.ticketRepository = ticketRepository;
    this.dependencyMapper = dependencyMapper;
  }

  /**
   * Adds a new dependency declaring that {@code blockedTicketId} is blocked by {@code
   * blockerTicketId}. Checks (in order): self-dependency, both tickets exist and are active,
   * same-project, no duplicate edge, no cycle introduced. A successful add fires a {@code CREATE}
   * audit-log entry via {@link com.att.tdp.issueflow.auditlog.AuditableEntityListener}.
   *
   * @param blockedTicketId the id of the ticket that is being blocked (from the URL path)
   * @param blockerTicketId the id of the blocker ticket (from {@code blockedBy} in the body)
   * @throws InvalidStateTransitionException if the request would be self-blocking, cross-project,
   *     or would close a cycle
   * @throws NotFoundException if either ticket does not exist or is soft-deleted
   * @throws DuplicateResourceException if the same (blocked, blocker) edge already exists
   */
  @Transactional
  public void addDependency(Long blockedTicketId, Long blockerTicketId) {
    if (blockedTicketId.equals(blockerTicketId)) {
      throw new InvalidStateTransitionException("A ticket cannot block itself");
    }

    Ticket blocked =
        ticketRepository
            .findByIdAndDeletedAtIsNull(blockedTicketId)
            .orElseThrow(() -> new NotFoundException(RESOURCE, blockedTicketId));
    Ticket blocker =
        ticketRepository
            .findByIdAndDeletedAtIsNull(blockerTicketId)
            .orElseThrow(() -> new NotFoundException(RESOURCE, blockerTicketId));

    if (!blocked.getProject().getId().equals(blocker.getProject().getId())) {
      throw new InvalidStateTransitionException(
          "Blocker must belong to the same project as the blocked ticket");
    }

    if (dependencyRepository.existsByBlocked_IdAndBlocker_Id(blockedTicketId, blockerTicketId)) {
      throw new DuplicateResourceException("blockedBy", String.valueOf(blockerTicketId));
    }

    if (introducesCycle(blockedTicketId, blockerTicketId)) {
      throw new InvalidStateTransitionException("Adding this dependency would introduce a cycle");
    }

    TicketDependency dependency = new TicketDependency();
    dependency.setBlocked(blocked);
    dependency.setBlocker(blocker);
    dependencyRepository.save(dependency);
  }

  /**
   * Returns the active blockers for an active ticket as {@link DependencyResponse} records. The
   * underlying query filters out blockers whose ticket has been soft-deleted — those are treated as
   * resolved and never surface on the API.
   *
   * @param ticketId the blocked ticket id (from the URL path)
   * @return active blockers, ordered by blocker ticket id ascending
   * @throws NotFoundException if no active ticket has {@code ticketId}
   */
  @Transactional(readOnly = true)
  public List<DependencyResponse> listDependencies(Long ticketId) {
    ticketRepository
        .findByIdAndDeletedAtIsNull(ticketId)
        .orElseThrow(() -> new NotFoundException(RESOURCE, ticketId));

    return dependencyRepository.findActiveBlockersByBlockedTicketId(ticketId).stream()
        .map(dependencyMapper::toResponse)
        .toList();
  }

  /**
   * Hard-deletes the dependency row identified by the (blocked, blocker) pair. Fires a {@code
   * DELETE} audit-log entry via the entity listener.
   *
   * @param blockedTicketId the blocked ticket id (from the URL path)
   * @param blockerTicketId the blocker ticket id (from the URL path)
   * @throws NotFoundException if the blocked ticket does not exist or is soft-deleted, or if no
   *     dependency row exists for the pair
   */
  @Transactional
  public void removeDependency(Long blockedTicketId, Long blockerTicketId) {
    ticketRepository
        .findByIdAndDeletedAtIsNull(blockedTicketId)
        .orElseThrow(() -> new NotFoundException(RESOURCE, blockedTicketId));

    TicketDependency dependency =
        dependencyRepository
            .findByBlocked_IdAndBlocker_Id(blockedTicketId, blockerTicketId)
            .orElseThrow(() -> new NotFoundException("Dependency", blockerTicketId));

    dependencyRepository.delete(dependency);
  }

  /**
   * Returns whether inserting an edge {@code blocked → blocker} would close a cycle in the
   * dependency graph. Performs a DFS from {@code blockerTicketId} along existing {@code
   * findBlockerIdsByBlockedTicketId} edges; if {@code blockedTicketId} is reachable from the new
   * blocker, the proposed edge would create a cycle.
   */
  private boolean introducesCycle(Long blockedTicketId, Long blockerTicketId) {
    Set<Long> visited = new HashSet<>();
    Deque<Long> stack = new ArrayDeque<>();
    stack.push(blockerTicketId);

    while (!stack.isEmpty()) {
      Long current = stack.pop();
      if (!visited.add(current)) {
        continue;
      }
      if (current.equals(blockedTicketId)) {
        return true;
      }
      for (Long next : dependencyRepository.findBlockerIdsByBlockedTicketId(current)) {
        if (!visited.contains(next)) {
          stack.push(next);
        }
      }
    }
    return false;
  }
}
