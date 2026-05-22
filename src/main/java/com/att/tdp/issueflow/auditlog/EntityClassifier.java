package com.att.tdp.issueflow.auditlog;

import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.user.User;

/**
 * Maps a JPA entity instance to its {@link AuditEntityType} and extracts its primary-key id. Adding
 * a new audited entity requires two lines here: a new enum value in {@link AuditEntityType} and a
 * new {@code case} in each method below.
 */
public final class EntityClassifier {

  private EntityClassifier() {}

  /**
   * Returns the audit entity type for the given entity.
   *
   * @param entity the JPA entity instance
   * @return the matching {@link AuditEntityType}
   * @throws IllegalArgumentException if the entity class is not audited
   */
  public static AuditEntityType classify(Object entity) {
    return switch (entity) {
      case User ignored -> AuditEntityType.USER;
      case Project ignored -> AuditEntityType.PROJECT;
      case Ticket ignored -> AuditEntityType.TICKET;
      case Comment ignored -> AuditEntityType.COMMENT;
      default ->
          throw new IllegalArgumentException(
              "Unaudited entity class: " + entity.getClass().getName());
    };
  }

  /**
   * Returns the primary-key id of the given entity.
   *
   * @param entity the JPA entity instance
   * @return the entity id
   * @throws IllegalArgumentException if the entity class is not audited
   */
  public static Long extractId(Object entity) {
    return switch (entity) {
      case User u -> u.getId();
      case Project p -> p.getId();
      case Ticket t -> t.getId();
      case Comment c -> c.getId();
      default ->
          throw new IllegalArgumentException(
              "Unaudited entity class: " + entity.getClass().getName());
    };
  }
}
