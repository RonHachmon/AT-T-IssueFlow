package com.att.tdp.issueflow.user;

/**
 * The role assigned to a {@link User}. The set is exhaustive in this phase — extending it (e.g.
 * adding {@code REPORTER}) is a new feature, not a configuration tweak.
 */
public enum Role {
  /** Full administrative privileges. */
  ADMIN,

  /** Standard developer role. */
  DEVELOPER
}
