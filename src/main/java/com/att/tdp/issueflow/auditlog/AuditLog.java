package com.att.tdp.issueflow.auditlog;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable audit record. Every column except {@code id} and {@code details} is marked {@code
 * updatable = false} — combined with the absence of any write endpoint on {@code /audit-logs}, this
 * makes the record unmodifiable through any normal application code path.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, updatable = false, length = 32)
  private AuditAction action;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, updatable = false, length = 32)
  private AuditEntityType entityType;

  @Column(name = "entity_id", nullable = false, updatable = false)
  private Long entityId;

  @Column(name = "performed_by", updatable = false)
  private Long performedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor", nullable = false, updatable = false, length = 16)
  private ActorKind actor;

  @Column(name = "timestamp", nullable = false, updatable = false)
  private Instant timestamp;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "details", columnDefinition = "jsonb")
  private Map<String, Object> details;
}
