package com.att.tdp.issueflow.mention;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.comment.CommentMapper;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.mention.CommentMentionRepository;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.common.pagination.PagedResponse;
import com.att.tdp.issueflow.user.UserRepository;

/**
 * Business logic for the {@code GET /users/:userId/mentions} surface. Returns comments where the
 * given user is {@code @}-mentioned, hiding any mention whose ticket or project is soft-deleted —
 * the JPQL in {@link CommentMentionRepository#findMentioningCommentsForUser} enforces that filter
 * at the database.
 */
@Service
public class MentionService {

  private final UserRepository userRepository;
  private final CommentMentionRepository commentMentionRepository;
  private final CommentMapper commentMapper;

  public MentionService(
      UserRepository userRepository,
      CommentMentionRepository commentMentionRepository,
      CommentMapper commentMapper) {
    this.userRepository = userRepository;
    this.commentMentionRepository = commentMentionRepository;
    this.commentMapper = commentMapper;
  }

  /**
   * Returns one page of comments mentioning the user, newest first. The controller exposes a
   * 1-based {@code page}; this method translates it to Spring Data's 0-based index.
   *
   * @param userId the mentioned user's id
   * @param page the 1-based page number requested
   * @param pageSize the page size requested
   * @return a {@link PagedResponse} echoing the request page/pageSize and the total mention count
   * @throws NotFoundException if no user has {@code userId}
   */
  @Transactional(readOnly = true)
  public PagedResponse<CommentResponse> listMentionsForUser(Long userId, int page, int pageSize) {
    if (!userRepository.existsById(userId)) {
      throw new NotFoundException("User", userId);
    }
    Pageable pageable = PageRequest.of(page - 1, pageSize);
    Page<Comment> result = commentMentionRepository.findMentioningCommentsForUser(userId, pageable);
    List<CommentResponse> data =
        result.getContent().stream().map(commentMapper::toResponse).toList();
    return new PagedResponse<>(data, page, pageSize, result.getTotalElements());
  }
}
