package com.att.tdp.issueflow.ticket.dependency.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for adding a blocker to a ticket.
 *
 * @param blockedBy the id of the ticket that must be {@code DONE} before the path ticket can be
 *     transitioned to {@code DONE}
 */
public record AddDependencyRequest(@NotNull Long blockedBy) {}
