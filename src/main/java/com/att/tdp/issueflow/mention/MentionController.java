package com.att.tdp.issueflow.mention;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.common.pagination.PagedResponse;

/**
 * REST controller exposing {@code GET /users/:userId/mentions}. Lives in its own slice (rather than
 * the user slice) so it can depend on comment internals without making the user package leak the
 * mention abstraction.
 *
 * <p>{@code @Validated} on the class enables Bean Validation on {@code @RequestParam} arguments —
 * out-of-range values raise a {@code ConstraintViolationException} mapped to {@code 422} by the
 * global advice.
 */
@RestController
@RequestMapping("/users/{userId}/mentions")
@Validated
public class MentionController {

  private final MentionService mentionService;

  public MentionController(MentionService mentionService) {
    this.mentionService = mentionService;
  }

  /**
   * Returns one page of comments mentioning the given user, newest first.
   *
   * @param userId the mentioned user's id (path)
   * @param page 1-based page number; defaults to 1
   * @param pageSize page size; defaults to 20, capped at 100
   * @return {@code 200 OK} with the canonical {@link PagedResponse} envelope
   */
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public PagedResponse<CommentResponse> listMentions(
      @PathVariable Long userId,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    return mentionService.listMentionsForUser(userId, page, pageSize);
  }
}
