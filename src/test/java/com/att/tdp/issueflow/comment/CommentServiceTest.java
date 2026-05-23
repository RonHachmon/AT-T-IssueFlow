package com.att.tdp.issueflow.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.comment.mention.MentionExtractor;
import com.att.tdp.issueflow.common.error.ForbiddenException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

  @Mock private CommentRepository commentRepository;
  @Mock private TicketRepository ticketRepository;
  @Mock private UserRepository userRepository;
  @Mock private CommentMapper commentMapper;
  @Mock private MentionExtractor mentionExtractor;

  @InjectMocks private CommentService commentService;

  private Ticket activeTicket;
  private User author;
  private Comment savedComment;
  private CommentResponse commentResponse;
  private CreateCommentRequest createRequest;

  @BeforeEach
  void setup() {
    activeTicket = new Ticket();
    activeTicket.setId(1L);

    author = new User();
    author.setId(2L);
    author.setUsername("jdoe");

    savedComment = new Comment();
    savedComment.setId(10L);
    savedComment.setContent("This is a comment");
    savedComment.setTicket(activeTicket);
    savedComment.setAuthor(author);
    savedComment.setVersion(0L);

    commentResponse = new CommentResponse(10L, 1L, 2L, "This is a comment", List.of());
    createRequest = new CreateCommentRequest(2L, "This is a comment");
  }

  // ---------------- createComment ----------------

  @Test
  void createsCommentSuccessfully() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(activeTicket));
    when(userRepository.findByUsernameIgnoreCase("jdoe")).thenReturn(Optional.of(author));
    Comment unmapped = new Comment();
    unmapped.setContent("This is a comment");
    when(commentMapper.toEntity(createRequest)).thenReturn(unmapped);
    when(mentionExtractor.extract("This is a comment")).thenReturn(List.of());
    when(commentRepository.save(unmapped)).thenReturn(savedComment);
    when(commentMapper.toResponse(savedComment)).thenReturn(commentResponse);

    CommentResponse result = commentService.createComment(1L, createRequest, "jdoe");

    assertThat(result).isEqualTo(commentResponse);
    assertThat(unmapped.getTicket()).isEqualTo(activeTicket);
    assertThat(unmapped.getAuthor()).isEqualTo(author);
    assertThat(unmapped.getMentions()).isEmpty();
    verify(commentRepository).save(unmapped);
  }

  @Test
  void createsCommentWithResolvedMentions() {
    User alice = new User();
    alice.setId(3L);
    alice.setUsername("alice");

    CreateCommentRequest request = new CreateCommentRequest(2L, "Hey @jdoe and @alice");
    Comment unmapped = new Comment();
    unmapped.setContent("Hey @jdoe and @alice");

    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(activeTicket));
    when(userRepository.findByUsernameIgnoreCase("jdoe")).thenReturn(Optional.of(author));
    when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
    when(commentMapper.toEntity(request)).thenReturn(unmapped);
    when(mentionExtractor.extract("Hey @jdoe and @alice")).thenReturn(List.of("jdoe", "alice"));
    when(commentRepository.save(unmapped)).thenReturn(savedComment);
    when(commentMapper.toResponse(savedComment)).thenReturn(commentResponse);

    commentService.createComment(1L, request, "jdoe");

    assertThat(unmapped.getMentions())
        .hasSize(2)
        .extracting(m -> m.getUser().getId())
        .containsExactlyInAnyOrder(2L, 3L);
  }

  @Test
  void createsCommentSilentlyIgnoresUnknownHandles() {
    CreateCommentRequest request = new CreateCommentRequest(2L, "Hi @nobody");
    Comment unmapped = new Comment();
    unmapped.setContent("Hi @nobody");

    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(activeTicket));
    when(userRepository.findByUsernameIgnoreCase("jdoe")).thenReturn(Optional.of(author));
    when(userRepository.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());
    when(commentMapper.toEntity(request)).thenReturn(unmapped);
    when(mentionExtractor.extract("Hi @nobody")).thenReturn(List.of("nobody"));
    when(commentRepository.save(unmapped)).thenReturn(savedComment);
    when(commentMapper.toResponse(savedComment)).thenReturn(commentResponse);

    commentService.createComment(1L, request, "jdoe");

    assertThat(unmapped.getMentions()).isEmpty();
    verify(commentRepository).save(unmapped);
  }

  @Test
  void selfMentionAllowed() {
    CreateCommentRequest request = new CreateCommentRequest(2L, "Note to self: @jdoe");
    Comment unmapped = new Comment();
    unmapped.setContent("Note to self: @jdoe");

    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(activeTicket));
    when(userRepository.findByUsernameIgnoreCase("jdoe")).thenReturn(Optional.of(author));
    when(commentMapper.toEntity(request)).thenReturn(unmapped);
    when(mentionExtractor.extract("Note to self: @jdoe")).thenReturn(List.of("jdoe"));
    when(commentRepository.save(unmapped)).thenReturn(savedComment);
    when(commentMapper.toResponse(savedComment)).thenReturn(commentResponse);

    commentService.createComment(1L, request, "jdoe");

    assertThat(unmapped.getMentions())
        .hasSize(1)
        .extracting(m -> m.getUser().getId())
        .containsExactly(2L);
  }

  @Test
  void throwsNotFoundWhenTicketAbsentOnCreate() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.createComment(99L, createRequest, "jdoe"))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("99");
    verify(commentRepository, never()).save(any());
  }

  @Test
  void throwsNotFoundWhenAuthorNotFound() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(activeTicket));
    when(userRepository.findByUsernameIgnoreCase("jdoe")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.createComment(1L, createRequest, "jdoe"))
        .isInstanceOf(NotFoundException.class);
    verify(commentRepository, never()).save(any());
  }

  @Test
  void throwsForbiddenWhenAuthorIdDoesNotMatchToken() {
    CreateCommentRequest wrongIdRequest = new CreateCommentRequest(999L, "This is a comment");
    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(activeTicket));
    when(userRepository.findByUsernameIgnoreCase("jdoe")).thenReturn(Optional.of(author));

    assertThatThrownBy(() -> commentService.createComment(1L, wrongIdRequest, "jdoe"))
        .isInstanceOf(ForbiddenException.class);
    verify(commentRepository, never()).save(any());
  }

  // ---------------- listComments ----------------

  @Test
  void listsCommentsOrderedByCreatedAt() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(activeTicket));
    when(commentRepository.findAllByTicketIdOrderByCreatedAtAsc(1L))
        .thenReturn(List.of(savedComment));
    when(commentMapper.toResponse(savedComment)).thenReturn(commentResponse);

    List<CommentResponse> result = commentService.listComments(1L);

    assertThat(result).containsExactly(commentResponse);
  }

  @Test
  void throwsNotFoundWhenTicketAbsentOnList() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.listComments(99L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("99");
  }

  // ---------------- updateComment ----------------

  @Test
  void updatesCommentSuccessfully() {
    UpdateCommentRequest request = new UpdateCommentRequest("Updated text");
    when(commentRepository.findById(10L)).thenReturn(Optional.of(savedComment));
    lenient().when(mentionExtractor.extract("Updated text")).thenReturn(List.of());
    when(commentRepository.save(savedComment)).thenReturn(savedComment);

    commentService.updateComment(1L, 10L, request);

    assertThat(savedComment.getContent()).isEqualTo("Updated text");
    verify(commentRepository).save(savedComment);
  }

  @Test
  void updateCommentReplacesMentionSet() {
    User alice = new User();
    alice.setId(3L);
    alice.setUsername("alice");
    User charlie = new User();
    charlie.setId(4L);
    charlie.setUsername("charlie");

    // Seed the existing mentions on the saved comment (jdoe + alice from the prior content).
    savedComment.replaceMentions(List.of(author, alice));

    UpdateCommentRequest request = new UpdateCommentRequest("Now mentioning @alice and @charlie");
    when(commentRepository.findById(10L)).thenReturn(Optional.of(savedComment));
    when(mentionExtractor.extract("Now mentioning @alice and @charlie"))
        .thenReturn(List.of("alice", "charlie"));
    when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
    when(userRepository.findByUsernameIgnoreCase("charlie")).thenReturn(Optional.of(charlie));
    when(commentRepository.save(savedComment)).thenReturn(savedComment);

    commentService.updateComment(1L, 10L, request);

    assertThat(savedComment.getMentions())
        .hasSize(2)
        .extracting(m -> m.getUser().getId())
        .containsExactlyInAnyOrder(3L, 4L);
  }

  @Test
  void updateCommentRemovingAllMentionsClearsThem() {
    User alice = new User();
    alice.setId(3L);
    alice.setUsername("alice");
    savedComment.replaceMentions(List.of(author, alice));

    UpdateCommentRequest request = new UpdateCommentRequest("no mentions at all");
    when(commentRepository.findById(10L)).thenReturn(Optional.of(savedComment));
    when(mentionExtractor.extract("no mentions at all")).thenReturn(List.of());
    when(commentRepository.save(savedComment)).thenReturn(savedComment);

    commentService.updateComment(1L, 10L, request);

    assertThat(savedComment.getMentions()).isEmpty();
  }

  @Test
  void throwsNotFoundWhenCommentAbsentOnUpdate() {
    UpdateCommentRequest request = new UpdateCommentRequest("Updated text");
    when(commentRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.updateComment(1L, 999L, request))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("999");
    verify(commentRepository, never()).save(any());
  }

  // ---------------- deleteComment ----------------

  @Test
  void deletesCommentSuccessfully() {
    when(commentRepository.findById(10L)).thenReturn(Optional.of(savedComment));

    commentService.deleteComment(1L, 10L);

    verify(commentRepository).delete(savedComment);
  }

  @Test
  void throwsNotFoundWhenCommentAbsentOnDelete() {
    when(commentRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.deleteComment(1L, 999L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("999");
    verify(commentRepository, never()).delete(any());
  }
}
