package com.att.tdp.issueflow.user;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link User}. Inherits standard CRUD plus {@link
 * org.springframework.data.domain.Pageable} support.
 */
public interface UserRepository extends JpaRepository<User, Long> {

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
