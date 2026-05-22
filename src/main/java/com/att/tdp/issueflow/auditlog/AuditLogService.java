package com.att.tdp.issueflow.auditlog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.auditlog.dto.AuditLogFilter;
import com.att.tdp.issueflow.auditlog.dto.AuditLogResponse;
import com.att.tdp.issueflow.common.error.InvalidFilterException;
import com.att.tdp.issueflow.common.security.SecurityUtil;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;

/**
 * Write path: {@link #record(AuditAction, Object)} is called by {@link AuditableEntityListener}
 * inside the same transaction as the triggering entity change.
 *
 * <p>Read path: {@link #findAll(AuditLogFilter)} applies optional AND-combined filters and returns
 * entries newest-first as a flat list. Only the {@code ADMIN} role may call the read path (enforced
 * at the controller by {@code @PreAuthorize}).
 *
 * <p>Actor resolution: if the current thread's {@link
 * org.springframework.security.core.context.SecurityContextHolder} yields a known username that
 * resolves to a database user, {@code performedBy} is set to that user's id and {@code actor} is
 * {@code USER}. Otherwise both fall back to {@code null} / {@code SYSTEM}.
 */
@Service
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;
  private final UserRepository userRepository;
  private final AuditLogMapper auditLogMapper;

  public AuditLogService(
      AuditLogRepository auditLogRepository,
      UserRepository userRepository,
      AuditLogMapper auditLogMapper) {
    this.auditLogRepository = auditLogRepository;
    this.userRepository = userRepository;
    this.auditLogMapper = auditLogMapper;
  }

  /**
   * Creates an audit entry for the given entity change. Must be called within an active transaction
   * (the listener guarantees this).
   *
   * @param action the semantic action — may carry a hint set by the calling service
   * @param entity the JPA entity that changed; must be one of the audited types
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditAction action, Object entity) {
    Optional<Long> userId =
        SecurityUtil.currentUsername()
            .flatMap(username -> userRepository.findByUsernameIgnoreCase(username))
            .map(User::getId);

    AuditLog log = new AuditLog();
    log.setAction(action);
    log.setEntityType(EntityClassifier.classify(entity));
    log.setEntityId(EntityClassifier.extractId(entity));
    log.setPerformedBy(userId.orElse(null));
    log.setActor(userId.isPresent() ? ActorKind.USER : ActorKind.SYSTEM);
    log.setTimestamp(Instant.now());

    auditLogRepository.save(log);
  }

  /**
   * Returns all audit entries matching the given filter, ordered by {@code timestamp} descending.
   *
   * @param filter the query filter; {@link AuditLogFilter#validate()} is called first
   * @return matching entries, newest first
   * @throws InvalidFilterException if {@code entityId} is supplied without {@code entityType}
   */
  @Transactional(readOnly = true)
  public List<AuditLogResponse> findAll(AuditLogFilter filter) {
    filter.validate();
    Specification<AuditLog> spec = buildSpec(filter);
    List<AuditLog> entries =
        auditLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "timestamp"));
    return auditLogMapper.toResponseList(entries);
  }

  private Specification<AuditLog> buildSpec(AuditLogFilter filter) {
    Specification<AuditLog> spec = Specification.where(null);
    if (filter.entityType() != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), filter.entityType()));
    }
    if (filter.entityId() != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("entityId"), filter.entityId()));
    }
    if (filter.action() != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), filter.action()));
    }
    if (filter.actor() != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("actor"), filter.actor()));
    }
    return spec;
  }
}
