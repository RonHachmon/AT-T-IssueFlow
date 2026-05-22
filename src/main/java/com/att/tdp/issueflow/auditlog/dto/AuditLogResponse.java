package com.att.tdp.issueflow.auditlog.dto;

import java.time.Instant;

import com.att.tdp.issueflow.auditlog.ActorKind;
import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;

/** Read-only projection of an {@code AuditLog} entry. {@code details} is intentionally omitted. */
public record AuditLogResponse(
    Long id,
    AuditAction action,
    AuditEntityType entityType,
    Long entityId,
    Long performedBy,
    ActorKind actor,
    Instant timestamp) {}
