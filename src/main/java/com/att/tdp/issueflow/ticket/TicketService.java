package com.att.tdp.issueflow.ticket;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditContext;
import com.att.tdp.issueflow.common.error.InvalidStateTransitionException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.dependency.TicketDependencyRepository;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;

/**
 * Business logic for the tickets surface. All HTTP-status mapping happens outside this class — the
 * service throws semantic exceptions ({@link NotFoundException}, {@link
 * InvalidStateTransitionException}) and the {@code @RestControllerAdvice} maps them to status
 * codes.
 *
 * <p>Every read uses {@code findByIdAndDeletedAtIsNull} so soft-deleted tickets are invisible
 * without any additional filtering at the call site.
 */
@Service
public class TicketService {

  private static final String RESOURCE = "Ticket";

  private final TicketRepository ticketRepository;
  private final ProjectRepository projectRepository;
  private final UserRepository userRepository;
  private final TicketMapper ticketMapper;
  private final TicketDependencyRepository ticketDependencyRepository;

  public TicketService(
      TicketRepository ticketRepository,
      ProjectRepository projectRepository,
      UserRepository userRepository,
      TicketMapper ticketMapper,
      TicketDependencyRepository ticketDependencyRepository) {
    this.ticketRepository = ticketRepository;
    this.projectRepository = projectRepository;
    this.userRepository = userRepository;
    this.ticketMapper = ticketMapper;
    this.ticketDependencyRepository = ticketDependencyRepository;
  }

  /**
   * Creates a new ticket. Resolves the owning project (must be active) and optional assignee, maps
   * the request to an entity with status {@code TODO}, saves, and returns the response.
   *
   * @param request the validated create request
   * @return the persisted ticket as a response DTO
   * @throws NotFoundException if no active project has the given {@code projectId}, or if {@code
   *     assigneeId} is supplied but no user with that id exists
   */
  @Transactional
  public TicketResponse create(CreateTicketRequest request) {
    Project project =
        projectRepository
            .findByIdAndDeletedAtIsNull(request.projectId())
            .orElseThrow(() -> new NotFoundException("Project", request.projectId()));

    User assignee = null;
    if (request.assigneeId() != null) {
      assignee =
          userRepository
              .findById(request.assigneeId())
              .orElseThrow(() -> new NotFoundException("User", request.assigneeId()));
    }

    Ticket ticket = ticketMapper.toEntity(request, project, assignee);
    Ticket saved = ticketRepository.save(ticket);
    return ticketMapper.toResponse(saved);
  }

  /**
   * Fetches a single active ticket by id.
   *
   * @param id the ticket identifier
   * @return the ticket as a response DTO
   * @throws NotFoundException if no active ticket has that id
   */
  @Transactional(readOnly = true)
  public TicketResponse getById(Long id) {
    return ticketRepository
        .findByIdAndDeletedAtIsNull(id)
        .map(ticketMapper::toResponse)
        .orElseThrow(() -> new NotFoundException(RESOURCE, id));
  }

  /**
   * Returns all active tickets belonging to a given project, ordered by id ascending. Soft-deleted
   * tickets are excluded. The project itself must also be active.
   *
   * @param projectId the owning project identifier
   * @return all active tickets for the project, ordered by id ascending
   * @throws NotFoundException if no active project has the given {@code projectId}
   */
  @Transactional(readOnly = true)
  public List<TicketResponse> listByProject(Long projectId) {
    projectRepository
        .findByIdAndDeletedAtIsNull(projectId)
        .orElseThrow(() -> new NotFoundException("Project", projectId));

    return ticketRepository.findAllByProjectIdAndDeletedAtIsNullOrderByIdAsc(projectId).stream()
        .map(ticketMapper::toResponse)
        .toList();
  }

  /**
   * Applies a partial update to an active ticket. Handles both field changes and status
   * transitions. DONE tickets are frozen — any update attempt returns a conflict.
   *
   * <p>Status transitions enforce a forward-only rule: only the immediate next state is accepted.
   * Backward and skip-step transitions throw {@link InvalidStateTransitionException}.
   *
   * <p>{@code assigneeId} uses {@link java.util.Optional} to distinguish "field absent" (leave
   * unchanged) from "explicitly null" (clear assignee) from "present with value" (reassign).
   *
   * @param id the ticket identifier
   * @param request the validated partial update
   * @throws NotFoundException if no active ticket has that id, or if the requested assignee user
   *     does not exist
   * @throws InvalidStateTransitionException if the ticket is in {@code DONE} status, or if the
   *     requested status transition is not a single forward step
   */
  @Transactional
  public void update(Long id, UpdateTicketRequest request) {
    Ticket ticket =
        ticketRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(RESOURCE, id));

    if (ticket.getStatus().isTerminal()) {
      throw new InvalidStateTransitionException(
          "Ticket is frozen — DONE tickets cannot be modified");
    }

    if (request.status() != null) {
      validateTransition(ticket.getStatus(), request.status());
      if (request.status() == TicketStatus.DONE
          && ticketDependencyRepository.countActiveOpenBlockers(ticket.getId()) > 0) {
        throw new InvalidStateTransitionException(
            "Ticket cannot be marked DONE while it has open blockers");
      }
      ticket.setStatus(request.status());
      // status changes are audited as STATUS_CHANGE; see auditlog package
      AuditContext.hint(AuditAction.STATUS_CHANGE);
    }

    if (request.title() != null) {
      ticket.setTitle(request.title());
    }
    if (request.description() != null) {
      ticket.setDescription(request.description());
    }
    if (request.priority() != null) {
      ticket.setPriority(request.priority());
    }
    if (request.type() != null) {
      ticket.setType(request.type());
    }
    if (request.dueDate() != null) {
      ticket.setDueDate(request.dueDate());
    }
    if (request.assigneeId() != null) {
      if (request.assigneeId().isPresent()) {
        Long assigneeId = request.assigneeId().get();
        User assignee =
            userRepository
                .findById(assigneeId)
                .orElseThrow(() -> new NotFoundException("User", assigneeId));
        ticket.setAssignee(assignee);
      } else {
        ticket.setAssignee(null);
      }
    }

    ticketRepository.save(ticket);
  }

  /**
   * Soft-deletes a ticket by setting its {@code deletedAt} timestamp. Subsequent reads via {@link
   * #getById} or {@link #listByProject} will exclude this ticket. Calling soft-delete on an
   * already-deleted ticket raises {@link NotFoundException} because {@code
   * findByIdAndDeletedAtIsNull} returns empty.
   *
   * @param id the ticket identifier
   * @throws NotFoundException if no active ticket has that id, or if the ticket is already deleted
   */
  @Transactional
  public void softDelete(Long id) {
    Ticket ticket =
        ticketRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(RESOURCE, id));

    ticket.setDeletedAt(Instant.now());
    AuditContext.hint(AuditAction.SOFT_DELETE);
    ticketRepository.save(ticket);
  }

  private void validateTransition(TicketStatus current, TicketStatus requested) {
    if (!current.isImmediateSuccessor(requested)) {
      TicketStatus[] values = TicketStatus.values();
      String nextValid =
          (current.ordinal() + 1 < values.length)
              ? values[current.ordinal() + 1].name()
              : "none (terminal)";
      throw new InvalidStateTransitionException(
          "Status can only advance one step forward: "
              + current
              + " → next valid is "
              + nextValid
              + ", but requested "
              + requested);
    }
  }
}
