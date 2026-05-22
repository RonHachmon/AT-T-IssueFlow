package com.att.tdp.issueflow.auditlog;

import java.util.List;

import org.springframework.stereotype.Component;

import com.att.tdp.issueflow.auditlog.dto.AuditLogResponse;

@Component
public class AuditLogMapper {

  public AuditLogResponse toResponse(AuditLog entity) {
    return new AuditLogResponse(
        entity.getId(),
        entity.getAction(),
        entity.getEntityType(),
        entity.getEntityId(),
        entity.getPerformedBy(),
        entity.getActor(),
        entity.getTimestamp());
  }

  public List<AuditLogResponse> toResponseList(List<AuditLog> entities) {
    return entities.stream().map(this::toResponse).toList();
  }
}
