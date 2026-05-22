package com.att.tdp.issueflow.auditlog;

/** The kind of entity described by an audit entry. */
public enum AuditEntityType {
  USER,
  PROJECT,
  TICKET,
  COMMENT,
  TICKET_DEPENDENCY,
  ATTACHMENT
}
