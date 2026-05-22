package com.att.tdp.issueflow.comment;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.common.error.ForbiddenException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;

/**
 * Business logic for the comments surface. Throws semantic exceptions ({@link NotFoundException})
 * that the {@code @RestControllerAdvice} maps to HTTP status codes. No HTTP-layer types appear in
 * this class.
 */
@Service
public class CommentService {

  private static final String RESOURCE = "Comment";

  private final CommentRepository commentRepository;
  private final TicketRepository ticketRepository;
  private final UserRepository userRepository;
  private final CommentMapper commentMapper;

  public CommentService(
      CommentRepository commentRepository,
      TicketRepository ticketRepository,
      UserRepository userRepository,
      CommentMapper commentMapper) {
    this.commentRepository = commentRepository;
    this.ticketRepository = ticketRepository;
    this.userRepository = userRepository;
    this.commentMapper = commentMapper;
  }

  /**
   * Posts a new comment on an active ticket. The ticket must not be soft-deleted, the {@code
   * authorUsername} must resolve to a known user, and {@code request.authorId()} must match that
   * user's database id — preventing a caller from posting as a different user.
   *
   * @param ticketId the owning ticket identifier
   * @param request the validated comment body including {@code authorId} and {@code content}
   * @param authorUsername the username extracted from the caller's JWT
   * @return the persisted comment as a response DTO
   * @throws NotFoundException if no active ticket has {@code ticketId}, or if {@code
   *     authorUsername} is not in the database
   * @throws ForbiddenException if {@code request.authorId()} does not match the authenticated user
   */
  @Transactional
  public CommentResponse createComment(
      Long ticketId, CreateCommentRequest request, String authorUsername) {
    Ticket ticket =
        ticketRepository
            .findByIdAndDeletedAtIsNull(ticketId)
            .orElseThrow(() -> new NotFoundException("Ticket", ticketId));

    User authenticatedUser =
        userRepository
            .findByUsernameIgnoreCase(authorUsername)
            .orElseThrow(() -> new NotFoundException("User", null));

    if (!authenticatedUser.getId().equals(request.authorId())) {
      throw new ForbiddenException("authorId does not match the authenticated user");
    }

    Comment comment = commentMapper.toEntity(request);
    comment.setTicket(ticket);
    comment.setAuthor(authenticatedUser);

    Comment saved = commentRepository.save(comment);
    return commentMapper.toResponse(saved);
  }

  /**
   * Returns all comments for an active ticket ordered by creation time ascending as a plain list.
   *
   * @param ticketId the owning ticket identifier
   * @return all comments for the ticket, oldest first
   * @throws NotFoundException if no active ticket has {@code ticketId}
   */
  @Transactional(readOnly = true)
  public List<CommentResponse> listComments(Long ticketId) {
    ticketRepository
        .findByIdAndDeletedAtIsNull(ticketId)
        .orElseThrow(() -> new NotFoundException("Ticket", ticketId));

    return commentRepository.findAllByTicketIdOrderByCreatedAtAsc(ticketId).stream()
        .map(commentMapper::toResponse)
        .toList();
  }

  /**
   * Updates the content of an existing comment.
   *
   * @param ticketId the owning ticket identifier (used for path correctness)
   * @param commentId the comment identifier
   * @param request the validated update body
   * @throws NotFoundException if no comment with {@code commentId} exists
   */
  @Transactional
  public void updateComment(Long ticketId, Long commentId, UpdateCommentRequest request) {
    Comment comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new NotFoundException(RESOURCE, commentId));

    comment.setContent(request.content());
    commentRepository.save(comment);
  }

  /**
   * Hard-deletes a comment. The comment is permanently removed and will not appear in subsequent
   * list responses.
   *
   * @param ticketId the owning ticket identifier (used for path correctness)
   * @param commentId the comment identifier
   * @throws NotFoundException if no comment with {@code commentId} exists
   */
  @Transactional
  public void deleteComment(Long ticketId, Long commentId) {
    Comment comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new NotFoundException(RESOURCE, commentId));

    commentRepository.delete(comment);
  }
}
