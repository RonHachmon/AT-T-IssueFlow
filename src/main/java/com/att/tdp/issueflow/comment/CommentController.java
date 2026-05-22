package com.att.tdp.issueflow.comment;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;

/**
 * REST controller for the comments surface. All endpoints are nested under {@code
 * /tickets/{ticketId}/comments}. Every success response returns {@code 200 OK} per the project-wide
 * README convention. Validation triggering lives here; business logic lives in {@link
 * CommentService}.
 */
@RestController
@RequestMapping("/tickets/{ticketId}/comments")
public class CommentController {

  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  /**
   * Posts a new comment on the given ticket. The author is identified by the bearer token; the
   * {@code authorId} in the request body must match the authenticated caller's id.
   *
   * @param ticketId the owning ticket identifier
   * @param request the validated comment body including {@code authorId} and {@code content}
   * @param principal the Spring Security principal resolved from the bearer token
   * @return {@code 200 OK} with the persisted comment
   */
  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  public CommentResponse createComment(
      @PathVariable Long ticketId,
      @Valid @RequestBody CreateCommentRequest request,
      @AuthenticationPrincipal UserDetails principal) {
    return commentService.createComment(ticketId, request, principal.getUsername());
  }

  /**
   * Returns all comments on the given ticket as a plain array ordered by creation time ascending.
   *
   * @param ticketId the owning ticket identifier
   * @return {@code 200 OK} with all comments for the ticket, oldest first
   */
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public List<CommentResponse> listComments(@PathVariable Long ticketId) {
    return commentService.listComments(ticketId);
  }

  /**
   * Updates a comment's content.
   *
   * @param ticketId the owning ticket identifier
   * @param commentId the comment identifier
   * @param request the validated update body
   */
  @PatchMapping("/{commentId}")
  @ResponseStatus(HttpStatus.OK)
  public void updateComment(
      @PathVariable Long ticketId,
      @PathVariable Long commentId,
      @Valid @RequestBody UpdateCommentRequest request) {
    commentService.updateComment(ticketId, commentId, request);
  }

  /**
   * Hard-deletes a comment. Returns {@code 200 OK} with no body. A second call with the same id
   * returns {@code 404} because the comment no longer exists.
   *
   * @param ticketId the owning ticket identifier
   * @param commentId the comment identifier
   */
  @DeleteMapping("/{commentId}")
  @ResponseStatus(HttpStatus.OK)
  public void deleteComment(@PathVariable Long ticketId, @PathVariable Long commentId) {
    commentService.deleteComment(ticketId, commentId);
  }
}
