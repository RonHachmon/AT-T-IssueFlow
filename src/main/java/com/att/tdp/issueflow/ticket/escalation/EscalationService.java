package com.att.tdp.issueflow.ticket.escalation;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditContext;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;

/**
 * Walks every overdue, active, non-{@code DONE} ticket and bumps its priority one rung up the
 * ladder. Once a ticket has reached {@link TicketPriority#CRITICAL}, no further work is done — the
 * {@code isOverdue} field on the response already signals the overdue state.
 *
 * <p>Concurrency: the outer pass {@link #runEscalation()} runs without a surrounding transaction
 * and dispatches each candidate to {@link #escalateOne(Long)}, which carries
 * {@code @Transactional(propagation = REQUIRES_NEW)}. {@link
 * ObjectOptimisticLockingFailureException} from a concurrent user write is caught and logged at
 * WARN — the next tick will revisit the ticket and finish the work.
 *
 * <p>This service deliberately bypasses {@code TicketService.update} because the latter enforces a
 * {@code DONE}-frozen guard and applies validations that do not belong on a system-driven priority
 * change. {@code DONE} tickets are already excluded by the upstream id query, so the bypass is
 * safe.
 */
@Service
public class EscalationService {

  private static final Logger LOG = LoggerFactory.getLogger(EscalationService.class);

  private final TicketRepository ticketRepository;
  private final EscalationService self;

  public EscalationService(TicketRepository ticketRepository, @Lazy EscalationService self) {
    this.ticketRepository = ticketRepository;
    this.self = self;
  }

  /**
   * Collects every overdue, active, non-{@code DONE} ticket id and asks the proxy to escalate each
   * one in its own transaction. Optimistic-lock failures on individual tickets are absorbed so a
   * single conflict cannot poison the rest of the pass.
   */
  public void runEscalation() {
    Instant now = Instant.now();
    List<Long> candidates = ticketRepository.findIdsOfOverdueNonDoneActiveTickets(now);
    LOG.debug("auto-escalation: {} candidate ticket(s)", candidates.size());

    for (Long id : candidates) {
      try {
        self.escalateOne(id);
      } catch (ObjectOptimisticLockingFailureException conflict) {
        LOG.warn(
            "auto-escalation: skipping ticket {} due to concurrent write; next tick will retry",
            id);
      } catch (RuntimeException unexpected) {
        LOG.warn("auto-escalation: skipping ticket {} after unexpected error", id, unexpected);
      }
    }
  }

  /**
   * Performs a single escalation step on one ticket, in its own transaction. Re-checks the
   * preconditions inside the transaction because the candidate list was assembled outside it and
   * the world may have shifted.
   *
   * @param id the ticket identifier
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void escalateOne(Long id) {
    Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(id).orElse(null);
    if (ticket == null) {
      return;
    }
    if (ticket.getStatus().isTerminal()) {
      return;
    }
    Instant dueDate = ticket.getDueDate();
    if (dueDate == null || !dueDate.isBefore(Instant.now())) {
      return;
    }

    TicketPriority current = ticket.getPriority();
    if (current.isMax()) {
      return;
    }

    TicketPriority bumped = current.next();
    ticket.setPriority(bumped);
    AuditContext.hint(AuditAction.AUTO_ESCALATE);
    ticketRepository.save(ticket);
  }
}
