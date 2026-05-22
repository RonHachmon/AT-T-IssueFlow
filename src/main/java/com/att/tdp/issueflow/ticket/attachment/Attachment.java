package com.att.tdp.issueflow.ticket.attachment;

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

import com.att.tdp.issueflow.auditlog.AuditableEntityListener;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.user.User;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "attachments")
@EntityListeners(AuditableEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Attachment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
  private Ticket ticket;

  @Column(name = "filename", nullable = false, length = 255)
  private String filename;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "storage_key", nullable = false, unique = true, length = 512)
  private String storageKey;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "uploaded_by", nullable = false, updatable = false)
  private User uploadedBy;

  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt;

  @PrePersist
  void onCreate() {
    uploadedAt = Instant.now();
  }
}
