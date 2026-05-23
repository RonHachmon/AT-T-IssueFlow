package com.att.tdp.issueflow.ticket;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link Ticket}. The {@code AndDeletedAtIsNull} clause on every
 * finder ensures soft-deleted tickets are invisible to standard reads.
 */
public interface TicketRepository extends JpaRepository<Ticket, Long> {

  /**
   * Fetches one active ticket by id. Returns empty if the id does not exist or the ticket has been
   * soft-deleted.
   *
   * @param id the ticket identifier
   * @return an {@link Optional} containing the ticket, or empty if not found or soft-deleted
   */
  Optional<Ticket> findByIdAndDeletedAtIsNull(Long id);

  /**
   * Returns all active tickets belonging to the given project, ordered by id ascending.
   * Soft-deleted tickets are excluded.
   *
   * @param projectId the owning project identifier
   * @return all active tickets for the project, ordered by id ascending
   */
  List<Ticket> findAllByProjectIdAndDeletedAtIsNullOrderByIdAsc(Long projectId);

  /**
   * Returns all soft-deleted tickets belonging to the given project, ordered by id ascending. The
   * inverse of {@link #findAllByProjectIdAndDeletedAtIsNullOrderByIdAsc} — used by the admin-only
   * "list deleted" endpoint.
   *
   * @param projectId the owning project identifier
   * @return all soft-deleted tickets for the project, ordered by id ascending
   */
  List<Ticket> findAllByProjectIdAndDeletedAtIsNotNullOrderByIdAsc(Long projectId);

  /**
   * Fetches one soft-deleted ticket by id. Returns empty if the id does not exist or the ticket is
   * still active — used by the admin-only "restore" endpoint, where the operation is only
   * meaningful for currently-deleted records.
   *
   * @param id the ticket identifier
   * @return an {@link Optional} containing the ticket, or empty if not found or still active
   */
  Optional<Ticket> findByIdAndDeletedAtIsNotNull(Long id);

  /**
   * Returns the ids of every active, not-{@code DONE} ticket whose {@code dueDate} is strictly
   * before {@code now}. Used by the auto-escalation scheduler to identify candidates for a priority
   * bump or overdue-flag set. Only ids are returned so the outer pass stays cheap; each candidate
   * is re-fetched inside its own transaction by the service.
   *
   * @param now the cutoff timestamp; tickets with {@code dueDate < now} are returned
   * @return ids of overdue, non-terminal, active tickets
   */
  @Query(
      "SELECT t.id FROM Ticket t "
          + "WHERE t.deletedAt IS NULL "
          + "AND t.status <> com.att.tdp.issueflow.ticket.TicketStatus.DONE "
          + "AND t.dueDate IS NOT NULL "
          + "AND t.dueDate < :now")
  List<Long> findIdsOfOverdueNonDoneActiveTickets(@Param("now") Instant now);
}
