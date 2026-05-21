package com.att.tdp.issueflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;

class IssueFlowUserDetailsServiceTest {

  private UserRepository userRepository;
  private IssueFlowUserDetailsService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    service = new IssueFlowUserDetailsService(userRepository);
  }

  @Test
  void returnsUserDetailsForExistingUsername() {
    User user = new User();
    user.setUsername("alice");
    user.setPasswordHash("$2a$10$hashedpassword");
    user.setRole(Role.DEVELOPER);
    when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));

    UserDetails result = service.loadUserByUsername("alice");

    assertThat(result.getUsername()).isEqualTo("alice");
    assertThat(result.getAuthorities()).hasSize(1);
    assertThat(result.getAuthorities().iterator().next().getAuthority()).isEqualTo("DEVELOPER");
  }

  @Test
  void throwsUsernameNotFoundExceptionWhenUserDoesNotExist() {
    when(userRepository.findByUsernameIgnoreCase("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.loadUserByUsername("unknown"))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
