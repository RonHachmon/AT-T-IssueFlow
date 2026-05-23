package com.att.tdp.issueflow.project.workload.dto;

/**
 * Workload entry returned by {@code GET /projects/{projectId}/workload}: one developer and the
 * number of open tickets currently assigned to them in the project.
 *
 * @param userId developer's user id
 * @param username developer's username
 * @param openTicketCount count of non-{@code DONE}, non-soft-deleted tickets assigned to this
 *     developer in the project
 */
public record WorkloadResponse(Long userId, String username, Long openTicketCount) {}
