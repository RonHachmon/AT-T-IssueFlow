package com.att.tdp.issueflow.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Wires the single {@link PasswordEncoder} bean used to hash and verify passwords.
 *
 * <p>The bean is registered in this feature even though no endpoint accepts a password yet. This
 * keeps the Phase-2 authentication feature additive — it can inject {@link PasswordEncoder} via
 * field name without touching configuration. The return type is the {@link PasswordEncoder}
 * interface so the concrete algorithm can be swapped later (e.g. Argon2) without changing
 * callsites.
 */
@Configuration
public class PasswordEncoderConfiguration {

  /**
   * Provides the BCrypt-backed password encoder.
   *
   * @return a singleton {@link BCryptPasswordEncoder}
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
