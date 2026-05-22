package com.att.tdp.issueflow.ticket.dependency;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.att.tdp.issueflow.ticket.Ticket;

/**
 * Spring Data JPA repository for {@link TicketDependency}. Dependency rows are hard-deleted — no
 * soft-delete filter is needed on this table. The Ticket-side soft-delete is respected by the
 * custom queries: rows referencing a soft-deleted blocker are filtered at query time.
 */
public interface TicketDependencyRepository extends JpaRepository<TicketDependency, Long> {

  /**
   * Returns whether a dependency row already exists with the given (blocked, blocker) pair. Used as
   * a friendly pre-check before {@code save(...)} to throw {@code DuplicateResourceException}
   * before the database UNIQUE constraint fires.
   *
   * @param blockedTicketId the blocked ticket id
   * @param blockerTicketId the blocker ticket id
   * @return {@code true} if such a row exists
   */
  boolean existsByBlocked_IdAndBlocker_Id(Long blockedTicketId, Long blockerTicketId);

  /**
   * Fetches a single dependency row by its (blocked, blocker) pair. Used by the DELETE endpoint.
   *
   * @param blockedTicketId the blocked ticket id
   * @param blockerTicketId the blocker ticket id
   * @return the matching row, or empty if none
   */
  Optional<TicketDependency> findByBlocked_IdAndBlocker_Id(
      Long blockedTicketId, Long blockerTicketId);

  /**
   * Returns the ids of every blocker ticket for the given blocked ticket. This single-column
   * projection is used by the cycle-detection DFS — it keeps the traversal cheap by avoiding
   * loading full {@link TicketDependency} entities.
   *
   * @param blockedTicketId the blocked ticket id
   * @return the ids of all blocker tickets
   */
  @Query("SELECT d.blocker.id FROM TicketDependency d WHERE d.blocked.id = :blockedTicketId")
  List<Long> findBlockerIdsByBlockedTicketId(@Param("blockedTicketId") Long blockedTicketId);

  /**
   * Returns the blocker tickets for the given blocked ticket, excluding any whose blocker has been
   * soft-deleted, ordered by ticket id ascending. Used by {@code GET
   * /tickets/{ticketId}/dependencies}.
   *
   * @param blockedTicketId the blocked ticket id
   * @return active blocker tickets, ordered by id ascending
   */
  @Query(
      "SELECT d.blocker FROM TicketDependency d "
          + "WHERE d.blocked.id = :blockedTicketId "
          + "AND d.blocker.deletedAt IS NULL "
          + "ORDER BY d.blocker.id ASC")
  List<Ticket> findActiveBlockersByBlockedTicketId(@Param("blockedTicketId") Long blockedTicketId);

  /**
   * Counts blockers that are still active (not soft-deleted) and not yet {@code DONE}. Used by the
   * DONE-transition gate in {@code TicketService.update}: a non-zero count blocks the transition.
   * Soft-deleted blockers are treated as resolved.
   *
   * @param blockedTicketId the blocked ticket id
   * @return the number of unresolved blockers
   */
  @Query(
      "SELECT COUNT(d) FROM TicketDependency d "
          + "WHERE d.blocked.id = :blockedTicketId "
          + "AND d.blocker.deletedAt IS NULL "
          + "AND d.blocker.status <> com.att.tdp.issueflow.ticket.TicketStatus.DONE")
  long countActiveOpenBlockers(@Param("blockedTicketId") Long blockedTicketId);
}
