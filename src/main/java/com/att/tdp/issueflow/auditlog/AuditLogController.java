package com.att.tdp.issueflow.auditlog;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.auditlog.dto.AuditLogFilter;
import com.att.tdp.issueflow.auditlog.dto.AuditLogResponse;
import com.att.tdp.issueflow.common.error.InvalidFilterException;

// on your GlobalExceptionHandler
// imports

/**
 * Read-only HTTP surface for the audit log. Only {@code GET /audit-logs} is mapped — the absence of
 * any write mapping ({@code POST}, {@code PUT}, {@code PATCH}, {@code DELETE}) is the primary
 * enforcement of FR-010: audit entries are immutable through the API surface. Spring returns {@code
 * 405 Method Not Allowed} for any unmapped method on this path.
 */
@RestController
@RequestMapping("/audit-logs")
public class AuditLogController {

  private final AuditLogService auditLogService;

  public AuditLogController(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  /**
   * Returns all audit entries matching the supplied filters, newest first. All four parameters are
   * optional and combine with AND semantics. {@code entityId} without {@code entityType} returns
   * {@code 400 Bad Request}. Unknown enum values return {@code 400 Bad Request}.
   *
   * @param entityType restrict to a single entity kind
   * @param entityId restrict to a single record (requires entityType)
   * @param action restrict to a single action kind
   * @param actor restrict by actor kind (USER or SYSTEM)
   * @return matching audit entries, newest first
   */
  @GetMapping
  @PreAuthorize("hasAuthority('ADMIN')")
  @ResponseStatus(HttpStatus.OK)
  public List<AuditLogResponse> list(
      @RequestParam(required = false) AuditEntityType entityType,
      @RequestParam(required = false) Long entityId,
      @RequestParam(required = false) AuditAction action,
      @RequestParam(required = false) ActorKind actor) {

    // Enforce cross-parameter constraint explicitly so it doesn't get masked by a service mock
    if (entityId != null && entityType == null) {
      throw new InvalidFilterException("entityId requires entityType");
    }

    return auditLogService.findAll(new AuditLogFilter(entityType, entityId, action, actor));
  }
}
