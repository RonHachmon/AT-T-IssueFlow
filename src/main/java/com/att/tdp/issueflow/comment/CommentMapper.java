package com.att.tdp.issueflow.comment;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;

/**
 * Compile-time mapping between {@link Comment} and its DTOs. Server-managed fields ({@code id},
 * {@code ticket}, {@code author}, {@code createdAt}, {@code updatedAt}, {@code version}) are
 * explicitly ignored in the entity direction so new fields never silently leak across boundaries.
 * {@code mentionedUsers} is always mapped to an empty list; mention resolution is a future feature.
 */
@Mapper
public interface CommentMapper {

  /**
   * Maps a persistent {@link Comment} to its public response shape, flattening the ticket and
   * author associations to their ids and setting {@code mentionedUsers} to an empty list.
   *
   * @param comment the entity to project
   * @return a response DTO for transport to the caller
   */
  @Mapping(target = "ticketId", source = "ticket.id")
  @Mapping(target = "authorId", source = "author.id")
  @Mapping(target = "mentionedUsers", expression = "java(java.util.List.of())")
  CommentResponse toResponse(Comment comment);

  /**
   * Maps a validated create request to a new, unsaved {@link Comment} entity. All server-managed
   * fields are left unset and populated by JPA auditing or the database.
   *
   * @param request the validated create request
   * @return a fresh, unsaved {@link Comment} ready for the repository
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "ticket", ignore = true)
  @Mapping(target = "author", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "version", ignore = true)
  Comment toEntity(CreateCommentRequest request);
}
