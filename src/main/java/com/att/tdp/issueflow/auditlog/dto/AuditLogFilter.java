package com.att.tdp.issueflow.auditlog.dto;

import com.att.tdp.issueflow.auditlog.ActorKind;
import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditEntityType;
import com.att.tdp.issueflow.common.error.InvalidFilterException;

/**
 * Immutable query filter for {@code GET /audit-logs}. All four fields are optional; null means "no
 * restriction". Filters combine with AND semantics.
 *
 * <p>{@link #validate()} enforces the constraint that {@code entityId} requires {@code entityType}
 * because entity ids are not globally unique across entity types.
 */
public record AuditLogFilter(
    AuditEntityType entityType, Long entityId, AuditAction action, ActorKind actor) {

  /**
   * @throws InvalidFilterException if {@code entityId} is set without {@code entityType}
   */
  public void validate() {
    if (entityId != null && entityType == null) {
      throw new InvalidFilterException("entityId requires entityType to be specified");
    }
  }
}
