package com.att.tdp.issueflow.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A person known to IssueFlow. Owns no behavior beyond the lifecycle hooks that maintain {@code
 * createdAt} / {@code updatedAt}. {@code passwordHash} is intentionally nullable in this phase —
 * authentication arrives later, and the column lives here from day one so the {@code users} table
 * does not need to migrate.
 *
 * <p>The {@code @Column(unique = true)} annotations on {@code username} / {@code email} are kept as
 * field-level documentation only. With {@code spring.jpa.hibernate.ddl-auto: none}, the actual
 * uniqueness constraint is provided by the Flyway-managed functional unique indexes on {@code
 * LOWER(username)} / {@code LOWER(email)} so case-insensitive uniqueness is enforced without losing
 * the caller's original casing in storage.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "username", nullable = false, unique = true, length = 50)
  private String username;

  @Column(name = "email", nullable = false, unique = true, length = 254)
  private String email;

  @Column(name = "full_name", nullable = false, length = 100)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private Role role;

  @Column(name = "password_hash", length = 72)
  private String passwordHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
