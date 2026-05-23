package com.att.tdp.issueflow.mention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.comment.CommentMapper;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.mention.CommentMentionRepository;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.common.pagination.PagedResponse;
import com.att.tdp.issueflow.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class MentionServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private CommentMentionRepository commentMentionRepository;
  @Mock private CommentMapper commentMapper;

  @InjectMocks private MentionService mentionService;

  private CommentResponse response(long id) {
    return new CommentResponse(id, 1L, 2L, "content " + id, List.of());
  }

  @Test
  void returnsPagedCommentsMentioningUserNewestFirst() {
    Comment c2 = new Comment();
    c2.setId(2L);
    Comment c1 = new Comment();
    c1.setId(1L);
    Page<Comment> page = new PageImpl<>(List.of(c2, c1), PageRequest.of(0, 20), 2);

    when(userRepository.existsById(5L)).thenReturn(true);
    when(commentMentionRepository.findMentioningCommentsForUser(eq(5L), any(Pageable.class)))
        .thenReturn(page);
    CommentResponse r2 = response(2L);
    CommentResponse r1 = response(1L);
    when(commentMapper.toResponse(c2)).thenReturn(r2);
    when(commentMapper.toResponse(c1)).thenReturn(r1);

    PagedResponse<CommentResponse> result = mentionService.listMentionsForUser(5L, 1, 20);

    assertThat(result.data()).containsExactly(r2, r1);
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.pageSize()).isEqualTo(20);
    assertThat(result.total()).isEqualTo(2L);
  }

  @Test
  void passesPageMinusOneToRepositoryForOneBasedRequest() {
    when(userRepository.existsById(5L)).thenReturn(true);
    Page<Comment> empty = new PageImpl<>(List.of(), PageRequest.of(2, 10), 0);
    when(commentMentionRepository.findMentioningCommentsForUser(eq(5L), any(Pageable.class)))
        .thenReturn(empty);

    mentionService.listMentionsForUser(5L, 3, 10);

    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    verify(commentMentionRepository).findMentioningCommentsForUser(eq(5L), captor.capture());
    assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
    assertThat(captor.getValue().getPageSize()).isEqualTo(10);
  }

  @Test
  void returnsEmptyPagedResponseWhenNoMentions() {
    when(userRepository.existsById(5L)).thenReturn(true);
    Page<Comment> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
    when(commentMentionRepository.findMentioningCommentsForUser(eq(5L), any(Pageable.class)))
        .thenReturn(empty);

    PagedResponse<CommentResponse> result = mentionService.listMentionsForUser(5L, 1, 20);

    assertThat(result.data()).isEmpty();
    assertThat(result.total()).isZero();
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.pageSize()).isEqualTo(20);
    verify(commentMapper, never()).toResponse(any(Comment.class));
  }

  @Test
  void throwsNotFoundForUnknownUserId() {
    when(userRepository.existsById(99L)).thenReturn(false);

    assertThatThrownBy(() -> mentionService.listMentionsForUser(99L, 1, 20))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("99");
    verifyNoInteractions(commentMentionRepository, commentMapper);
  }
}
