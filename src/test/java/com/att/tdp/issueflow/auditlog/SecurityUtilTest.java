package com.att.tdp.issueflow.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import com.att.tdp.issueflow.common.security.SecurityUtil;

class SecurityUtilTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returnsEmptyWhenNoAuthenticationPresent() {
    SecurityContextHolder.clearContext();

    Optional<String> result = SecurityUtil.currentUsername();

    assertThat(result).isEmpty();
  }

  @Test
  void returnsEmptyForAnonymousPrincipal() {
    var token = new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of());
    SecurityContextHolder.getContext().setAuthentication(token);

    Optional<String> result = SecurityUtil.currentUsername();

    assertThat(result).isEmpty();
  }

  @Test
  void returnsUsernameFromUserDetailsPrincipal() {
    var userDetails = new User("jdoe", "", List.of());
    var token =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(token);

    Optional<String> result = SecurityUtil.currentUsername();

    assertThat(result).contains("jdoe");
  }
}
