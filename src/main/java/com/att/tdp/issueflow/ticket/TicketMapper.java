package com.att.tdp.issueflow.ticket;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.user.User;

/**
 * Compile-time mapping between {@link Ticket} and its DTOs. The {@code componentModel="spring"} and
 * {@code unmappedTargetPolicy=ERROR} settings come from {@code pom.xml} compiler args. Explicit
 * {@link Mapping#ignore()} on server-managed fields prevents new entity fields from silently
 * leaking across the controller boundary.
 */
@Mapper
public interface TicketMapper {

  /**
   * Maps a validated create request and resolved domain objects to a new, unsaved {@link Ticket}
   * entity. Status is fixed to {@link TicketStatus#TODO}; all server-managed fields are left unset
   * and populated by lifecycle hooks or the database.
   *
   * @param request the validated create request
   * @param project the resolved owning {@link Project} (must not be {@code null})
   * @param assignee the resolved {@link User} assignee, or {@code null} if unassigned
   * @return a fresh, unsaved {@link Ticket} ready for the repository
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "project", source = "project")
  @Mapping(target = "assignee", source = "assignee")
  @Mapping(target = "description", source = "request.description")
  @Mapping(target = "status", expression = "java(com.att.tdp.issueflow.ticket.TicketStatus.TODO)")
  Ticket toEntity(CreateTicketRequest request, Project project, User assignee);

  /**
   * Maps a persistent {@link Ticket} entity to its public response shape. {@code projectId} and
   * {@code assigneeId} are extracted from the lazy-loaded associations; {@code isOverdue} is
   * computed inline.
   *
   * @param ticket the entity to project
   * @return a response DTO for transport to the caller
   */
  @Mapping(target = "projectId", source = "project.id")
  @Mapping(target = "assigneeId", source = "assignee.id")
  @Mapping(
      target = "isOverdue",
      expression =
          "java(ticket.getDueDate() != null && ticket.getDueDate().isBefore(java.time.Instant.now()))")
  TicketResponse toResponse(Ticket ticket);
}
