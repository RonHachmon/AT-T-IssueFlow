package com.att.tdp.issueflow.common.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;

/**
 * Seeds development/test users on startup. Skipped in the {@code prod} profile.
 * Passwords are encoded at runtime via the application's {@link PasswordEncoder},
 * so no manual BCrypt hash generation is needed.
 *
 * <p>Credentials: admin / Admin1234!  and  developer / Dev1234!
 */
@Component
@Profile("!prod")
public class DevDataInitializer implements ApplicationRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public DevDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    seedUser("admin", "admin@issueflow.local", "Admin User", Role.ADMIN, "Admin1234!");
    seedUser("developer", "developer@issueflow.local", "Dev User", Role.DEVELOPER, "Dev1234!");
  }

  private void seedUser(
      String username, String email, String fullName, Role role, String rawPassword) {
    if (userRepository.existsByUsernameIgnoreCase(username)) {
      return;
    }
    User user = new User();
    user.setUsername(username);
    user.setEmail(email);
    user.setFullName(fullName);
    user.setRole(role);
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    userRepository.save(user);
  }
}
