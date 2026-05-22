package com.att.tdp.issueflow.auditlog;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

import com.att.tdp.issueflow.common.config.SpringContextHolder;

/**
 * JPA entity listener that writes an {@link AuditLog} entry after every successful persist, update,
 * or removal on an annotated entity.
 *
 * <p>JPA instantiates this class by reflection — it is NOT a Spring bean, so Spring dependency
 * injection is not available. Instead, {@link SpringContextHolder#bean(Class)} is used to look up
 * the {@link AuditLogService} at callback time.
 *
 * <p>Semantic actions (SOFT_DELETE, RESTORE, STATUS_CHANGE) are communicated via {@link
 * AuditContext#hint(AuditAction)} set by the calling service immediately before {@code
 * repository.save(...)}. The hint is consumed and cleared by {@link
 * AuditContext#consumeOrDefault(AuditAction)} so it cannot leak into subsequent saves.
 */
public class AuditableEntityListener {

  /**
   * Records a {@link AuditAction#CREATE} entry (or the action hinted via {@link AuditContext})
   * after a new entity is persisted.
   *
   * @param entity the newly persisted entity
   */
  @PostPersist
  public void onCreate(Object entity) {
    SpringContextHolder.bean(AuditLogService.class)
        .record(AuditContext.consumeOrDefault(AuditAction.CREATE), entity);
  }

  /**
   * Records an {@link AuditAction#UPDATE} entry (or the action hinted via {@link AuditContext})
   * after an entity is updated. Services that perform semantically-richer updates (soft delete,
   * restore, status change) set the hint immediately before {@code repository.save(...)}.
   *
   * @param entity the updated entity
   */
  @PostUpdate
  public void onUpdate(Object entity) {
    SpringContextHolder.bean(AuditLogService.class)
        .record(AuditContext.consumeOrDefault(AuditAction.UPDATE), entity);
  }

  /**
   * Records a {@link AuditAction#DELETE} entry (or the action hinted via {@link AuditContext})
   * after an entity is hard-deleted.
   *
   * @param entity the removed entity
   */
  @PostRemove
  public void onRemove(Object entity) {
    SpringContextHolder.bean(AuditLogService.class)
        .record(AuditContext.consumeOrDefault(AuditAction.DELETE), entity);
  }
}
