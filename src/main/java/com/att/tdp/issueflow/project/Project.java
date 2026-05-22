package com.att.tdp.issueflow.project;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.att.tdp.issueflow.auditlog.AuditableEntityListener;
import com.att.tdp.issueflow.user.User;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A container that groups related tickets. Soft-deletion is modelled by setting {@code deletedAt}
 * to a non-null timestamp; all standard reads exclude rows where {@code deleted_at IS NOT NULL}.
 *
 * <p>{@code @PrePersist} initialises both {@code createdAt} and {@code updatedAt};
 * {@code @PreUpdate} refreshes {@code updatedAt} only. {@code deletedAt} is set explicitly by the
 * service on soft-delete — no lifecycle hook touches it.
 */
@Entity
@Table(name = "projects")
@EntityListeners(AuditableEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Project {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
