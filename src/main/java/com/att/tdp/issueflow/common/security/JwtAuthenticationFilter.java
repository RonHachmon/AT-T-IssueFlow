package com.att.tdp.issueflow.common.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Intercepts every request exactly once, extracts a bearer token from the {@code Authorization}
 * header, validates it, and populates the {@link SecurityContextHolder} when valid.
 *
 * <p>The filter always calls {@code chain.doFilter} — a missing or invalid token is not rejected
 * here. Rejection happens downstream in Spring Security's access-decision layer.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;
  private final UserDetailsService userDetailsService;

  public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
    this.jwtService = jwtService;
    this.userDetailsService = userDetailsService;
  }

  /**
   * Validates the bearer token (if present) and populates {@link SecurityContextHolder}. Always
   * delegates to the next filter regardless of token presence or validity.
   *
   * @param request the incoming HTTP request
   * @param response the HTTP response
   * @param filterChain the remaining filter chain
   * @throws ServletException if filter chain processing throws
   * @throws IOException if filter chain processing throws
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String token = extractBearerToken(request);
    if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      authenticateRequest(token, request);
    }

    filterChain.doFilter(request, response);
  }

  private String extractBearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    return null;
  }

  private void authenticateRequest(String token, HttpServletRequest request) {
    if (!jwtService.validateToken(token)) {
      return;
    }
    String username = jwtService.extractUsername(token);
    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
