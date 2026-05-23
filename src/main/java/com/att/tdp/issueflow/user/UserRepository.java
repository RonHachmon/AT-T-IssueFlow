package com.att.tdp.issueflow.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link User}. Inherits standard CRUD plus {@link
 * org.springframework.data.domain.Pageable} support.
 */
public interface UserRepository extends JpaRepository<User, Long> {

  /**
   * Returns the user whose username matches case-insensitively. Used by {@code
   * IssueFlowUserDetailsService} and auth endpoints to load the principal after token validation.
   *
   * @param username the username to look up (any casing accepted)
   * @return an {@link Optional} containing the matching user, or empty if none found
   */
  Optional<User> findByUsernameIgnoreCase(String username);

  /**
   * Checks whether any user exists with a matching username, comparing case-insensitively.
   *
   * @param username the username to look up
   * @return {@code true} if a user with the same username (any casing) exists
   */
  boolean existsByUsernameIgnoreCase(String username);

  /**
   * Checks whether any user exists with a matching email, comparing case-insensitively.
   *
   * @param email the email to look up
   * @return {@code true} if a user with the same email (any casing) exists
   */
  boolean existsByEmailIgnoreCase(String email);

  /**
   * Returns every {@code DEVELOPER} with their count of open (non-{@code DONE}, non-soft-deleted)
   * tickets in the given project, ordered ascending by workload then by {@code createdAt}.
   * Developers with zero assignments are included via {@code LEFT JOIN}.
   *
   * <p>Native SQL is used because the {@code LEFT JOIN ... GROUP BY ... ORDER BY COUNT()} shape is
   * awkward to express in JPQL without correlated subqueries. The result is bound to {@link
   * WorkloadRow} by column alias, so reordering the {@code SELECT} list is safe but renaming
   * aliases will break the projection.
   *
   * @param projectId the owning project identifier
   * @return one row per developer, sorted by ascending workload
   */
  @Query(
      value =
          """
          SELECT u.id AS userId, u.username AS username, COUNT(t.id) AS openTicketCount
          FROM users u
          LEFT JOIN tickets t ON t.assignee_id = u.id
              AND t.project_id = :projectId
              AND t.status <> 'DONE'
              AND t.deleted_at IS NULL
          WHERE u.role = 'DEVELOPER'
          GROUP BY u.id, u.username, u.created_at
          ORDER BY COUNT(t.id) ASC, u.created_at ASC
          """,
      nativeQuery = true)
  List<WorkloadRow> findWorkloadByProjectId(@Param("projectId") Long projectId);
}
