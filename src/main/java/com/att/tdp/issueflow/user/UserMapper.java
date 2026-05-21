package com.att.tdp.issueflow.user;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;

/**
 * Compile-time mapping between {@link User} and its DTOs. The {@code componentModel="spring"} and
 * {@code unmappedTargetPolicy=ERROR} settings come from {@code pom.xml} compiler args. Explicit
 * {@link Mapping#ignore()} on server-managed fields (id, timestamps) and on sensitive fields
 * (passwordHash) guarantees that a new field added to {@link User} fails the build until the mapper
 * is updated — preventing accidental leaks across the controller boundary.
 */
@Mapper
public interface UserMapper {

  /**
   * Maps an incoming create request to a new persistent entity. Server-managed fields are left
   * unset and populated by the entity's lifecycle hooks (timestamps) or by the database
   * (identifier). {@code passwordHash} is left null in this phase by design.
   *
   * @param request the incoming create request
   * @return a fresh, unsaved {@link User} ready for the repository
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "passwordHash", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  User toEntity(CreateUserRequest request);

  /**
   * Maps a persistent entity to its public response shape. {@code passwordHash} is intentionally
   * absent from {@link UserResponse}; MapStruct's policy ensures any new entity field must be
   * mapped or explicitly ignored.
   *
   * @param user the entity to project
   * @return a response DTO for transport to the caller
   */
  UserResponse toResponse(User user);
}
