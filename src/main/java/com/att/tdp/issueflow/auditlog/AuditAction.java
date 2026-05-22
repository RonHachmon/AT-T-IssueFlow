package com.att.tdp.issueflow.auditlog;

/**
 * Action recorded in an audit entry. AUTO_ASSIGN and AUTO_ESCALATE are reserved for later phases.
 */
public enum AuditAction {
  CREATE,
  UPDATE,
  DELETE,
  SOFT_DELETE,
  RESTORE,
  STATUS_CHANGE,
  AUTO_ASSIGN,
  AUTO_ESCALATE
}
