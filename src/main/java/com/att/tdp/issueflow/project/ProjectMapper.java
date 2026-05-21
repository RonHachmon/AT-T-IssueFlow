package com.att.tdp.issueflow.project;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.user.User;

/**
 * Compile-time mapping between {@link Project} and its DTOs. The {@code componentModel="spring"}
 * and {@code unmappedTargetPolicy=ERROR} settings come from {@code pom.xml} compiler args. Explicit
 * {@link Mapping#ignore()} on server-managed fields (id, timestamps) prevents a new entity field
 * from silently leaking across the controller boundary.
 */
@Mapper
public interface ProjectMapper {

  /**
   * Maps an incoming create request and a resolved owner to a new, unsaved {@link Project} entity.
   * Server-managed fields are left unset and populated by the entity's lifecycle hooks or database.
   *
   * @param request the validated create request
   * @param owner the resolved {@link User} entity (must not be {@code null})
   * @return a fresh, unsaved {@link Project} ready for the repository
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  @Mapping(target = "owner", source = "owner")
  Project toEntity(CreateProjectRequest request, User owner);

  /**
   * Maps a persistent {@link Project} entity to its public response shape.
   *
   * @param project the entity to project
   * @return a response DTO for transport to the caller
   */
  @Mapping(target = "ownerId", source = "owner.id")
  ProjectResponse toResponse(Project project);
}
