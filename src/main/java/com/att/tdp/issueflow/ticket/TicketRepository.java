package com.att.tdp.issueflow.ticket;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
