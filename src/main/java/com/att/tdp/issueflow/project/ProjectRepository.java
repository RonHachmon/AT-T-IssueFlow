package com.att.tdp.issueflow.project;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Project}. Derives all queries from method names; the {@code
 * AndDeletedAtIsNull} clause ensures soft-deleted projects are invisible to standard reads.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

  /**
   * Fetches one active project by id. Returns empty if the id does not exist or the project has
   * been soft-deleted.
   *
   * @param id the project identifier
   * @return an {@link Optional} containing the project, or empty if not found or soft-deleted
   */
  Optional<Project> findByIdAndDeletedAtIsNull(Long id);

  /**
   * Returns all active projects ordered by id ascending. Soft-deleted projects are excluded.
   *
   * @return all active projects, ordered by id ascending
   */
  List<Project> findAllByDeletedAtIsNullOrderByIdAsc();

  /**
   * Checks whether an active project with a matching name exists, comparing case-insensitively.
   * Used as a pre-save uniqueness guard; the database partial unique index provides the hard
   * constraint.
   *
   * @param name the project name to check
   * @return {@code true} if an active project with the same name (any casing) exists
   */
  boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);
}
