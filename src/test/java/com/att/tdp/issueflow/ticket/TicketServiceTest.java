package com.att.tdp.issueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.att.tdp.issueflow.common.error.InvalidStateTransitionException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.dependency.TicketDependencyRepository;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

  @Mock private TicketRepository ticketRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private UserRepository userRepository;
  @Mock private TicketMapper ticketMapper;
  @Mock private TicketDependencyRepository ticketDependencyRepository;

  @InjectMocks private TicketService ticketService;

  private Project activeProject;
  private User assignee;
  private Ticket activeTicket;
  private TicketResponse ticketResponse;
  private CreateTicketRequest createRequest;

  @BeforeEach
  void setup() {
    activeProject = new Project();
    activeProject.setId(1L);

    assignee = new User();
    assignee.setId(2L);

    activeTicket = new Ticket();
    activeTicket.setId(10L);
    activeTicket.setTitle("Fix login bug");
    activeTicket.setStatus(TicketStatus.TODO);
    activeTicket.setPriority(TicketPriority.HIGH);
    activeTicket.setType(TicketType.BUG);
    activeTicket.setProject(activeProject);

    ticketResponse =
        new TicketResponse(
            10L,
            "Fix login bug",
            null,
            TicketStatus.TODO,
            TicketPriority.HIGH,
            TicketType.BUG,
            1L,
            null,
            null,
            false);

    createRequest =
        new CreateTicketRequest(
            "Fix login bug", null, TicketPriority.HIGH, TicketType.BUG, 1L, null, null);
  }

  // ---------------- create ----------------

  @Test
  void createTicket_persistsAndReturnsResponse() {
    when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(activeProject));
    when(ticketMapper.toEntity(createRequest, activeProject, null)).thenReturn(activeTicket);
    when(ticketRepository.save(activeTicket)).thenReturn(activeTicket);
    when(ticketMapper.toResponse(activeTicket)).thenReturn(ticketResponse);

    TicketResponse result = ticketService.create(createRequest);

    assertThat(result).isEqualTo(ticketResponse);
    verify(ticketRepository).save(activeTicket);
  }

  @Test
  void createTicket_throwsNotFoundWhenProjectAbsent() {
    when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ticketService.create(createRequest))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("1");
    verify(ticketRepository, never()).save(any());
  }

  @Test
  void createTicket_throwsNotFoundWhenProjectSoftDeleted() {
    when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ticketService.create(createRequest))
        .isInstanceOf(NotFoundException.class);
    verify(ticketRepository, never()).save(any());
  }

  @Test
  void createTicket_throwsNotFoundWhenAssigneeAbsent() {
    CreateTicketRequest requestWithAssignee =
        new CreateTicketRequest(
            "Fix login bug", null, TicketPriority.HIGH, TicketType.BUG, 1L, 99L, null);
    when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(activeProject));
    when(userRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ticketService.create(requestWithAssignee))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("99");
    verify(ticketRepository, never()).save(any());
  }

  // ---------------- getById ----------------

  @Test
  void getById_returnsTicketWhenActive() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeTicket));
    when(ticketMapper.toResponse(activeTicket)).thenReturn(ticketResponse);

    TicketResponse result = ticketService.getById(10L);

    assertThat(result).isEqualTo(ticketResponse);
  }

  @Test
  void getById_throwsNotFoundWhenSoftDeleted() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> ticketService.getById(10L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Ticket")
        .hasMessageContaining("10");
  }

  // ---------------- listByProject ----------------

  @Test
  void listByProject_excludesSoftDeletedTickets() {
    when(projectRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(activeProject));
    when(ticketRepository.findAllByProjectIdAndDeletedAtIsNullOrderByIdAsc(1L))
        .thenReturn(List.of(activeTicket));
    when(ticketMapper.toResponse(activeTicket)).thenReturn(ticketResponse);

    List<TicketResponse> result = ticketService.listByProject(1L);

    assertThat(result).containsExactly(ticketResponse);
  }

  // ---------------- update (field changes) ----------------

  @Test
  void update_appliesPriorityChange() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeTicket));
    UpdateTicketRequest request =
        new UpdateTicketRequest(null, null, TicketPriority.CRITICAL, null, null, null, null);

    ticketService.update(10L, request);

    assertThat(activeTicket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
    verify(ticketRepository).save(activeTicket);
  }

  @Test
  void update_throwsConflictWhenTicketIsDone() {
    activeTicket.setStatus(TicketStatus.DONE);
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeTicket));
    UpdateTicketRequest request =
        new UpdateTicketRequest(null, null, TicketPriority.LOW, null, null, null, null);

    assertThatThrownBy(() -> ticketService.update(10L, request))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("frozen");
    verify(ticketRepository, never()).save(any());
  }

  // ---------------- update (status transitions) ----------------

  @Test
  void update_throwsConflictOnBackwardTransition() {
    activeTicket.setStatus(TicketStatus.IN_PROGRESS);
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeTicket));
    UpdateTicketRequest request =
        new UpdateTicketRequest(null, null, null, null, null, null, TicketStatus.TODO);

    assertThatThrownBy(() -> ticketService.update(10L, request))
        .isInstanceOf(InvalidStateTransitionException.class);
    verify(ticketRepository, never()).save(any());
  }

  @Test
  void update_throwsConflictOnSkipStepTransition() {
    activeTicket.setStatus(TicketStatus.TODO);
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeTicket));
    UpdateTicketRequest request =
        new UpdateTicketRequest(null, null, null, null, null, null, TicketStatus.DONE);

    assertThatThrownBy(() -> ticketService.update(10L, request))
        .isInstanceOf(InvalidStateTransitionException.class);
    verify(ticketRepository, never()).save(any());
  }

  @Test
  void update_advancesStatusWhenValidTransition() {
    activeTicket.setStatus(TicketStatus.TODO);
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeTicket));
    UpdateTicketRequest request =
        new UpdateTicketRequest(null, null, null, null, null, null, TicketStatus.IN_PROGRESS);

    ticketService.update(10L, request);

    assertThat(activeTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    verify(ticketRepository).save(activeTicket);
  }

  // ---------------- DONE-gate: open blockers ----------------

  @Test
  void update_rejectsDoneTransitionWhenOpenBlockerExists() {
    activeTicket.setStatus(TicketStatus.IN_REVIEW);
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeTicket));
    when(ticketDependencyRepository.countActiveOpenBlockers(10L)).thenReturn(1L);
    UpdateTicketRequest request =
        new UpdateTicketRequest(null, null, null, null, null, null, TicketStatus.DONE);

    assertThatThrownBy(() -> ticketService.update(10L, request))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("open blockers");
    assertThat(activeTicket.getStatus()).isEqualTo(TicketStatus.IN_REVIEW);
    verify(ticketRepository, never()).save(any());
  }

  @Test
  void update_allowsDoneTransitionWhenAllBlockersResolved() {
    activeTicket.setStatus(TicketStatus.IN_REVIEW);
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeTicket));
    when(ticketDependencyRepository.countActiveOpenBlockers(10L)).thenReturn(0L);
    UpdateTicketRequest request =
        new UpdateTicketRequest(null, null, null, null, null, null, TicketStatus.DONE);

    ticketService.update(10L, request);

    assertThat(activeTicket.getStatus()).isEqualTo(TicketStatus.DONE);
    verify(ticketRepository).save(activeTicket);
  }

  // ---------------- softDelete ----------------

  @Test
  void softDelete_setsDeletedAt() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeTicket));

    ticketService.softDelete(10L);

    assertThat(activeTicket.getDeletedAt()).isNotNull();
    assertThat(activeTicket.getDeletedAt()).isBeforeOrEqualTo(Instant.now());
    verify(ticketRepository).save(activeTicket);
  }
}
