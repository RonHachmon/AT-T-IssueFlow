package com.att.tdp.issueflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import com.att.tdp.issueflow.common.security.JwtProperties;
import com.att.tdp.issueflow.common.security.JwtService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-32-chars!!";
  private static final Duration TTL = Duration.ofMinutes(12);

  private JwtService jwtService;
  private UserDetails userDetails;

  @BeforeEach
  void setUp() {
    JwtProperties props = new JwtProperties(SECRET, TTL);
    jwtService = new JwtService(props);
    userDetails = new User("alice", "", List.of());
  }

  @Test
  void tokenContainsExpectedSubjectUserIdAndRoleClaims() {
    String token = jwtService.issueToken(userDetails, 42L, "DEVELOPER");

    var claims =
        Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(token)
            .getPayload();

    assertThat(claims.getSubject()).isEqualTo("alice");
    assertThat(claims.get("userId", Long.class)).isEqualTo(42L);
    assertThat(claims.get("role", String.class)).isEqualTo("DEVELOPER");
  }

  @Test
  void validTokenPassesValidation() {
    String token = jwtService.issueToken(userDetails, 1L, "ADMIN");

    assertThat(jwtService.validateToken(token)).isTrue();
  }

  @Test
  void expiredTokenIsRejected() {
    JwtProperties shortLived = new JwtProperties(SECRET, Duration.ofSeconds(-1));
    JwtService expiredService = new JwtService(shortLived);

    String token = expiredService.issueToken(userDetails, 1L, "DEVELOPER");

    assertThat(jwtService.validateToken(token)).isFalse();
  }

  @Test
  void tokenWithTamperedSignatureIsRejected() {
    String token = jwtService.issueToken(userDetails, 1L, "DEVELOPER");
    String tampered = token + "x";

    assertThat(jwtService.validateToken(tampered)).isFalse();
  }
}
