package com.att.tdp.issueflow.ticket;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.user.User;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A unit of work belonging to exactly one {@link Project}. Soft-deletion is modelled by setting
 * {@code deletedAt} to a non-null timestamp; all standard reads exclude rows where {@code
 * deleted_at IS NOT NULL}.
 *
 * <p>{@code @PrePersist} initialises both {@code createdAt} and {@code updatedAt};
 * {@code @PreUpdate} refreshes {@code updatedAt} only. {@code deletedAt} is set explicitly by the
 * service — no lifecycle hook touches it.
 *
 * <p>{@code version} is a JPA optimistic-lock column: concurrent writers receive a {@code
 * ObjectOptimisticLockingFailureException} mapped to {@code 409 Conflict}.
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private TicketStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority", nullable = false, length = 10)
  private TicketPriority priority;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 15)
  private TicketType type;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id", nullable = false, updatable = false)
  private Project project;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assignee_id")
  private User assignee;

  @Column(name = "due_date")
  private Instant dueDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

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
