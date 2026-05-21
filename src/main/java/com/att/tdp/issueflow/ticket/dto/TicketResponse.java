package com.att.tdp.issueflow.ticket.dto;

import java.time.Instant;

import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;

/**
 * Response shape returned by all {@code /tickets} endpoints. {@code isOverdue} is a computed field:
 * {@code true} when a non-null {@code dueDate} is strictly before the current instant.
 *
 * @param id primary key
 * @param title ticket title
 * @param description optional free-text description; may be {@code null}
 * @param status current lifecycle state
 * @param priority urgency level
 * @param type work classification
 * @param projectId identifier of the owning project
 * @param assigneeId identifier of the assigned user, or {@code null} if unassigned
 * @param dueDate optional deadline; {@code null} if not set
 * @param isOverdue {@code true} when {@code dueDate} is non-null and in the past
 */
public record TicketResponse(
    Long id,
    String title,
    String description,
    TicketStatus status,
    TicketPriority priority,
    TicketType type,
    Long projectId,
    Long assigneeId,
    Instant dueDate,
    boolean isOverdue) {}
