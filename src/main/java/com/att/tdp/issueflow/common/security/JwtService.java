package com.att.tdp.issueflow.common.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and validates HS256-signed JWTs for IssueFlow.
 *
 * <p>The signing key is derived once at construction time from {@link JwtProperties#secret()}.
 * Every issued token embeds three custom claims in addition to the standard {@code sub}, {@code
 * iat}, and {@code exp}: {@code userId} (the database PK), and {@code role} (plain role name, no
 * {@code ROLE_} prefix).
 */
@Service
public class JwtService {

  private static final String CLAIM_USER_ID = "userId";
  private static final String CLAIM_ROLE = "role";

  private final SecretKey signingKey;
  private final JwtProperties jwtProperties;

  public JwtService(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Issues a signed JWT for the given principal.
   *
   * @param userDetails the Spring Security principal (used for the {@code sub} claim)
   * @param userId the database primary key stored in the {@code userId} claim
   * @param role the plain role name (e.g. {@code ADMIN}) stored in the {@code role} claim
   * @return a compact, URL-safe JWT string
   */
  public String issueToken(UserDetails userDetails, Long userId, String role) {
    long nowMs = System.currentTimeMillis();
    long expiryMs = nowMs + jwtProperties.accessTokenTtl().toMillis();
    return Jwts.builder()
        .subject(userDetails.getUsername())
        .claim(CLAIM_USER_ID, userId)
        .claim(CLAIM_ROLE, role)
        .issuedAt(new Date(nowMs))
        .expiration(new Date(expiryMs))
        .signWith(signingKey)
        .compact();
  }

  /**
   * Validates a JWT string. Returns {@code false} for any parse, signature, or expiry failure
   * without throwing.
   *
   * @param token the compact JWT string to validate
   * @return {@code true} if the token is well-formed, correctly signed, and not expired
   */
  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Extracts the {@code sub} (username) claim from a token.
   *
   * @param token a valid compact JWT string
   * @return the username embedded in the token
   */
  public String extractUsername(String token) {
    return parseClaims(token).getSubject();
  }

  /**
   * Extracts the {@code userId} claim from a token.
   *
   * @param token a valid compact JWT string
   * @return the database primary key embedded in the token
   */
  public Long extractUserId(String token) {
    return parseClaims(token).get(CLAIM_USER_ID, Long.class);
  }

  /**
   * Extracts the {@code role} claim from a token.
   *
   * @param token a valid compact JWT string
   * @return the plain role name embedded in the token (e.g. {@code DEVELOPER})
   */
  public String extractRole(String token) {
    return parseClaims(token).get(CLAIM_ROLE, String.class);
  }

  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
  }
}
