package com.att.tdp.issueflow.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
