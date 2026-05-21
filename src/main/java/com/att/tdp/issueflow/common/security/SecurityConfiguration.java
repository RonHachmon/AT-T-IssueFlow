package com.att.tdp.issueflow.common.security;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.att.tdp.issueflow.auth.IssueFlowUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Central Spring Security configuration. Wires stateless JWT-based authentication with method
 * security enabled for future {@code @PreAuthorize} rules.
 *
 * <p>Authority strings use no {@code ROLE_} prefix — see {@code docs/decisions.md}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final IssueFlowUserDetailsService userDetailsService;
  private final PasswordEncoder passwordEncoder;
  private final ObjectMapper objectMapper;

  public SecurityConfiguration(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      IssueFlowUserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder,
      ObjectMapper objectMapper) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.userDetailsService = userDetailsService;
    this.passwordEncoder = passwordEncoder;
    this.objectMapper = objectMapper;
  }

  /**
   * Configures the HTTP security filter chain: CSRF disabled, stateless sessions, open paths for
   * login and health probes, JWT filter injected before the form-login filter, and a custom RFC
   * 7807 entry point for unauthenticated requests.
   *
   * @param http the {@link HttpSecurity} builder provided by Spring
   * @return the built {@link SecurityFilterChain}
   * @throws Exception if the security configuration cannot be applied
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/auth/login", "/health/**", "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .authenticationProvider(authenticationProvider())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(new Rfc7807AuthenticationEntryPoint(objectMapper)));

    return http.build();
  }

  /**
   * Provides a {@link DaoAuthenticationProvider} wired with the application's {@link
   * IssueFlowUserDetailsService} and BCrypt {@link PasswordEncoder}.
   *
   * @return the configured authentication provider
   */
  @Bean
  public DaoAuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  /**
   * Exposes the {@link AuthenticationManager} so the login endpoint can authenticate credentials
   * directly.
   *
   * @param config the {@link AuthenticationConfiguration} injected by Spring
   * @return the application-wide authentication manager
   * @throws Exception if the manager cannot be retrieved
   */
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }

  /** RFC 7807 entry point — returns {@code application/problem+json} on 401. */
  private static class Rfc7807AuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    Rfc7807AuthenticationEntryPoint(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException)
        throws IOException {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("type", URI.create("https://issueflow.local/errors/unauthorized").toString());
      body.put("title", "Unauthorized");
      body.put("status", HttpStatus.UNAUTHORIZED.value());
      body.put("detail", "Authentication is required to access this resource.");
      body.put("timestamp", Instant.now().toString());

      objectMapper.writeValue(response.getWriter(), body);
    }
  }
}
