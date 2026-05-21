package com.att.tdp.issueflow.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.auth.dto.LoginRequest;
import com.att.tdp.issueflow.auth.dto.TokenResponse;
import com.att.tdp.issueflow.common.security.JwtProperties;
import com.att.tdp.issueflow.common.security.JwtService;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserMapper;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.dto.UserResponse;

/**
 * Handles authentication endpoints: login, logout, and current-user identity.
 *
 * <p>{@code UserRepository} is injected directly (rather than via a dedicated service) because the
 * entity is needed for JWT claim values ({@code userId}, {@code role}) and the {@code /me} response
 * — both of which are not available from Spring Security's {@link UserDetails} alone. See {@code
 * docs/decisions.md} for the rationale.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;
  private final UserRepository userRepository;
  private final UserMapper userMapper;

  public AuthController(
      AuthenticationManager authenticationManager,
      JwtService jwtService,
      JwtProperties jwtProperties,
      UserRepository userRepository,
      UserMapper userMapper) {
    this.authenticationManager = authenticationManager;
    this.jwtService = jwtService;
    this.jwtProperties = jwtProperties;
    this.userRepository = userRepository;
    this.userMapper = userMapper;
  }

  /**
   * Authenticates the submitted credentials and returns a signed JWT.
   *
   * @param request the login credentials (username + password, both required)
   * @return a {@link TokenResponse} containing the access token, token type, and expiry in seconds
   */
  @PostMapping("/login")
  @ResponseStatus(HttpStatus.OK)
  public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.username(), request.password()));

    User user =
        userRepository
            .findByUsernameIgnoreCase(request.username())
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

    UserDetails userDetails =
        org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
            .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
            .authorities(user.getRole().name())
            .build();

    String token = jwtService.issueToken(userDetails, user.getId(), user.getRole().name());
    long expiresIn = jwtProperties.accessTokenTtl().toSeconds();

    return new TokenResponse(token, "Bearer", expiresIn);
  }

  /**
   * Returns the profile of the currently authenticated caller.
   *
   * @param principal the Spring Security principal resolved from the bearer token
   * @return the user's profile as a {@link UserResponse}
   */
  @GetMapping("/me")
  @ResponseStatus(HttpStatus.OK)
  public UserResponse me(@AuthenticationPrincipal UserDetails principal) {
    User user =
        userRepository
            .findByUsernameIgnoreCase(principal.getUsername())
            .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    return userMapper.toResponse(user);
  }

  /**
   * Stateless no-op logout. The client is responsible for discarding the token. Real token
   * revocation (blocklist, short TTL rotation) is deferred to a later auth phase.
   */
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.OK)
  public void logout() {}
}
