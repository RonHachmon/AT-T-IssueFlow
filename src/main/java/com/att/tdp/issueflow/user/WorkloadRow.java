package com.att.tdp.issueflow.user;

/**
 * Spring Data interface projection for the per-developer workload aggregation returned by {@link
 * UserRepository#findWorkloadByProjectId(Long)}. Column aliases in the native query must match
 * these accessor names so Spring can wire them up.
 */
public interface WorkloadRow {
  Long getUserId();

  String getUsername();

  Long getOpenTicketCount();
}
