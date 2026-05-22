package com.att.tdp.issueflow.ticket.dependency;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.att.tdp.issueflow.auditlog.AuditableEntityListener;
import com.att.tdp.issueflow.ticket.Ticket;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A directed edge declaring that one {@link Ticket} ({@code blocked}) cannot be marked {@code DONE}
 * until another ticket ({@code blocker}) is itself {@code DONE}. Rows are immutable: they are only
 * inserted and deleted, never mutated, so no {@code @Version} or {@code updatedAt} is needed.
 *
 * <p>The composite uniqueness {@code (blocked_ticket_id, blocker_ticket_id)} is enforced by the
 * database via the migration's UNIQUE constraint; the service additionally pre-checks to surface a
 * friendly {@code DuplicateResourceException} before hitting the constraint.
 */
@Entity
@Table(
    name = "ticket_dependencies",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_ticket_dependency",
            columnNames = {"blocked_ticket_id", "blocker_ticket_id"}))
@EntityListeners(AuditableEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class TicketDependency {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "blocked_ticket_id", nullable = false, updatable = false)
  private Ticket blocked;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "blocker_ticket_id", nullable = false, updatable = false)
  private Ticket blocker;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }
}
