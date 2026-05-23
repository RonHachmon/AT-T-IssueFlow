package com.att.tdp.issueflow.comment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.MentionedUserSummary;
import com.att.tdp.issueflow.comment.mention.CommentMention;
import com.att.tdp.issueflow.comment.mention.MentionExtractor;
import com.att.tdp.issueflow.user.User;

/**
 * Compile-time mapping between {@link Comment} and its DTOs. Server-managed fields ({@code id},
 * {@code ticket}, {@code author}, {@code createdAt}, {@code updatedAt}, {@code version}, {@code
 * mentions}) are explicitly ignored in the entity direction so new fields never silently leak
 * across boundaries. Auditing/versioning fields are intentionally omitted from {@link
 * CommentResponse}. The {@code mentionedUsers} list is derived from the persisted mention set and
 * ordered by first appearance in the comment text — the mapper re-parses the content via {@link
 * MentionExtractor} to recover that order without storing positional offsets.
 */
@Mapper(componentModel = "spring")
public abstract class CommentMapper {

  @Autowired protected MentionExtractor mentionExtractor;

  /**
   * Maps a persistent {@link Comment} to its public response shape, flattening the ticket and
   * author associations to their ids and populating {@code mentionedUsers} via {@link
   * #orderedMentions(Comment)}.
   *
   * @param comment the entity to project
   * @return a response DTO for transport to the caller
   */
  @Mapping(target = "ticketId", source = "ticket.id")
  @Mapping(target = "authorId", source = "author.id")
  @Mapping(target = "mentionedUsers", expression = "java(orderedMentions(comment))")
  public abstract CommentResponse toResponse(Comment comment);

  /**
   * Maps a validated create request to a new, unsaved {@link Comment} entity. All server-managed
   * fields are left unset and populated by JPA auditing, the service layer, or the database.
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
  @Mapping(target = "mentions", ignore = true)
  public abstract Comment toEntity(CreateCommentRequest request);

  /**
   * Builds the response {@code mentionedUsers} list by re-parsing the comment content to recover
   * textual order, then resolving each handle against the persisted mention set. Unknown handles
   * (already filtered out at create/update time) and duplicates are skipped.
   *
   * @param comment the comment whose mentions to project
   * @return the mentioned users in order of first appearance in the comment text; empty when there
   *     are none
   */
  protected List<MentionedUserSummary> orderedMentions(Comment comment) {
    Set<CommentMention> mentions = comment.getMentions();
    if (mentions == null || mentions.isEmpty()) {
      return List.of();
    }
    Map<String, User> byLowerUsername =
        mentions.stream()
            .map(CommentMention::getUser)
            .collect(
                Collectors.toMap(
                    u -> u.getUsername().toLowerCase(Locale.ROOT), u -> u, (a, b) -> a));

    List<MentionedUserSummary> out = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    for (String handle : mentionExtractor.extract(comment.getContent())) {
      User user = byLowerUsername.get(handle);
      if (user != null && seen.add(user.getId())) {
        out.add(new MentionedUserSummary(user.getId(), user.getUsername(), user.getFullName()));
      }
    }
    return out;
  }
}
