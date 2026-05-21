package com.att.tdp.issueflow.ticket.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketType;

/**
 * Request body for {@code POST /tickets}. {@code title}, {@code priority}, {@code type}, and {@code
 * projectId} are required. {@code description}, {@code assigneeId}, and {@code dueDate} are
 * optional. Status is intentionally absent — it defaults to {@code TODO} in the service.
 *
 * @param title ticket title — required, non-blank, max 255 characters
 * @param description optional free-text description; may be {@code null}
 * @param priority urgency level — required
 * @param type classification — required
 * @param projectId owning project identifier — required; the service verifies the project exists
 *     and is not soft-deleted
 * @param assigneeId optional user to assign; the service verifies the user exists when provided
 * @param dueDate optional deadline; used to compute {@code isOverdue} in responses
 */
public record CreateTicketRequest(
    @NotBlank @Size(max = 255) String title,
    String description,
    @NotNull TicketPriority priority,
    @NotNull TicketType type,
    @NotNull Long projectId,
    Long assigneeId,
    Instant dueDate) {}
