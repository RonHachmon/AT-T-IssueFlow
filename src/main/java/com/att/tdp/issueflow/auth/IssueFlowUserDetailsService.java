package com.att.tdp.issueflow.auth;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.att.tdp.issueflow.user.UserRepository;

/**
 * Loads {@link UserDetails} from the database by username for Spring Security's authentication
 * pipeline.
 *
 * <p>Authorities are plain role names ({@code ADMIN}, {@code DEVELOPER}) with no {@code ROLE_}
 * prefix. Future {@code @PreAuthorize} expressions must use {@code hasAuthority('ADMIN')}, not
 * {@code hasRole('ADMIN')}.
 */
@Service
public class IssueFlowUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  public IssueFlowUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * Looks up a user by username (case-insensitive) and returns a Spring Security {@link
   * UserDetails} with a single granted authority equal to the user's role name.
   *
   * @param username the username supplied by the caller (any casing)
   * @return a populated {@link UserDetails} ready for credential verification
   * @throws UsernameNotFoundException if no user with the given username exists
   */
  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    com.att.tdp.issueflow.user.User user =
        userRepository
            .findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

    String passwordHash = user.getPasswordHash() != null ? user.getPasswordHash() : "";
    return new User(
        user.getUsername(),
        passwordHash,
        List.of(new SimpleGrantedAuthority(user.getRole().name())));
  }
}
