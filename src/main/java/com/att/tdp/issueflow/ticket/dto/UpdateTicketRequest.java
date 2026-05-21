package com.att.tdp.issueflow.ticket.dto;

import java.time.Instant;
import java.util.Optional;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;

/**
 * Request body for {@code PATCH /tickets/{ticketId}}. All fields are individually optional, but at
 * least one must be non-null — the {@link #hasAtLeastOneFieldSet()} rule rejects empty requests.
 *
 * <p>{@code assigneeId} uses {@link Optional} to distinguish "field absent" (leave assignee
 * unchanged) from "field explicitly null" (clear the assignee). Jackson deserialises a missing key
 * as a {@code null} Optional reference, and {@code "assigneeId": null} as {@link Optional#empty()}.
 *
 * @param title new title (max 255 chars), or {@code null} to leave unchanged
 * @param description new description, or {@code null} to leave unchanged
 * @param priority new priority, or {@code null} to leave unchanged
 * @param type new type, or {@code null} to leave unchanged
 * @param assigneeId absent = leave unchanged; {@link Optional#empty()} = clear assignee; {@link
 *     Optional#of(Long)} = reassign
 * @param dueDate new due date, or {@code null} to leave unchanged
 * @param status requested next status; the service enforces forward-only transitions
 */
public record UpdateTicketRequest(
    @Size(max = 255) String title,
    String description,
    TicketPriority priority,
    TicketType type,
    Optional<Long> assigneeId,
    Instant dueDate,
    TicketStatus status) {

  /**
   * Bean Validation rule: at least one modifiable field must be supplied. Returning {@code false}
   * causes Spring to raise a 400 ProblemDetail naming this method.
   *
   * @return {@code true} when at least one field is non-null
   */
  @AssertTrue(message = "at least one field must be supplied")
  public boolean hasAtLeastOneFieldSet() {
    return title != null
        || description != null
        || priority != null
        || type != null
        || assigneeId != null
        || dueDate != null
        || status != null;
  }
}
