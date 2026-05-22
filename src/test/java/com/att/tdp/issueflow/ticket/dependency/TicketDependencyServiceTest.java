package com.att.tdp.issueflow.ticket.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.att.tdp.issueflow.common.error.DuplicateResourceException;
import com.att.tdp.issueflow.common.error.InvalidStateTransitionException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.dependency.dto.DependencyResponse;

@ExtendWith(MockitoExtension.class)
class TicketDependencyServiceTest {

  @Mock private TicketDependencyRepository dependencyRepository;
  @Mock private TicketRepository ticketRepository;
  @Mock private TicketDependencyMapper dependencyMapper;

  @InjectMocks private TicketDependencyService dependencyService;

  private Project projectA;
  private Project projectB;
  private Ticket blocked;
  private Ticket blocker;

  @BeforeEach
  void setup() {
    projectA = new Project();
    projectA.setId(1L);

    projectB = new Project();
    projectB.setId(2L);

    blocked = new Ticket();
    blocked.setId(10L);
    blocked.setProject(projectA);
    blocked.setStatus(TicketStatus.IN_REVIEW);

    blocker = new Ticket();
    blocker.setId(20L);
    blocker.setProject(projectA);
    blocker.setStatus(TicketStatus.IN_PROGRESS);
  }

  // ---------------- addDependency ----------------

  @Test
  void rejectsSelfDependency() {
    assertThatThrownBy(() -> dependencyService.addDependency(10L, 10L))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("cannot block itself");

    verify(dependencyRepository, never()).save(any());
  }

  @Test
  void notFoundWhenBlockedTicketMissing() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> dependencyService.addDependency(10L, 20L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("10");
    verify(dependencyRepository, never()).save(any());
  }

  @Test
  void notFoundWhenBlockerTicketMissing() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(blocked));
    when(ticketRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> dependencyService.addDependency(10L, 20L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("20");
    verify(dependencyRepository, never()).save(any());
  }

  @Test
  void rejectsBlockerFromDifferentProject() {
    blocker.setProject(projectB);
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(blocked));
    when(ticketRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(blocker));

    assertThatThrownBy(() -> dependencyService.addDependency(10L, 20L))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("same project");
    verify(dependencyRepository, never()).save(any());
  }

  @Test
  void rejectsDuplicateDependency() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(blocked));
    when(ticketRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(blocker));
    when(dependencyRepository.existsByBlocked_IdAndBlocker_Id(10L, 20L)).thenReturn(true);

    assertThatThrownBy(() -> dependencyService.addDependency(10L, 20L))
        .isInstanceOf(DuplicateResourceException.class);
    verify(dependencyRepository, never()).save(any());
  }

  @Test
  void rejectsCycle() {
    // existing edge: blocker(20) is blocked by blocked(10) — i.e. 20 → 10
    // adding 10 → 20 would close the cycle.
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(blocked));
    when(ticketRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(blocker));
    when(dependencyRepository.existsByBlocked_IdAndBlocker_Id(10L, 20L)).thenReturn(false);
    // DFS starts from blockerId=20, looking for blockedId=10.
    // 20 has blocker 10 → cycle detected.
    when(dependencyRepository.findBlockerIdsByBlockedTicketId(20L)).thenReturn(List.of(10L));

    assertThatThrownBy(() -> dependencyService.addDependency(10L, 20L))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("cycle");
    verify(dependencyRepository, never()).save(any());
  }

  @Test
  void rejectsTransitiveCycle() {
    // 20 → 30 → 10. Adding 10 → 20 closes a 3-node cycle.
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(blocked));
    when(ticketRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(blocker));
    when(dependencyRepository.existsByBlocked_IdAndBlocker_Id(10L, 20L)).thenReturn(false);
    when(dependencyRepository.findBlockerIdsByBlockedTicketId(20L)).thenReturn(List.of(30L));
    when(dependencyRepository.findBlockerIdsByBlockedTicketId(30L)).thenReturn(List.of(10L));

    assertThatThrownBy(() -> dependencyService.addDependency(10L, 20L))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("cycle");
    verify(dependencyRepository, never()).save(any());
  }

  @Test
  void addsDependencyOnHappyPath() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(blocked));
    when(ticketRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(blocker));
    when(dependencyRepository.existsByBlocked_IdAndBlocker_Id(10L, 20L)).thenReturn(false);
    when(dependencyRepository.findBlockerIdsByBlockedTicketId(20L)).thenReturn(List.of());

    dependencyService.addDependency(10L, 20L);

    verify(dependencyRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                d -> d.getBlocked() == blocked && d.getBlocker() == blocker));
  }

  // ---------------- listDependencies ----------------

  @Test
  void listsActiveBlockers() {
    DependencyResponse response = new DependencyResponse(20L, "Blocker title", TicketStatus.TODO);
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(blocked));
    when(dependencyRepository.findActiveBlockersByBlockedTicketId(10L))
        .thenReturn(List.of(blocker));
    when(dependencyMapper.toResponse(blocker)).thenReturn(response);

    List<DependencyResponse> result = dependencyService.listDependencies(10L);

    assertThat(result).containsExactly(response);
  }

  @Test
  void listThrowsNotFoundWhenTicketMissing() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> dependencyService.listDependencies(99L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("99");
  }

  // ---------------- removeDependency ----------------

  @Test
  void removeDependencyDeletesRow() {
    TicketDependency existing = new TicketDependency();
    existing.setId(100L);
    existing.setBlocked(blocked);
    existing.setBlocker(blocker);
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(blocked));
    when(dependencyRepository.findByBlocked_IdAndBlocker_Id(10L, 20L))
        .thenReturn(Optional.of(existing));

    dependencyService.removeDependency(10L, 20L);

    verify(dependencyRepository).delete(existing);
  }

  @Test
  void removeDependencyNotFoundWhenBlockedTicketMissing() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> dependencyService.removeDependency(99L, 20L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("99");
    verify(dependencyRepository, never()).delete(any(TicketDependency.class));
  }

  @Test
  void removeDependencyNotFoundWhenDependencyMissing() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(blocked));
    when(dependencyRepository.findByBlocked_IdAndBlocker_Id(10L, 20L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> dependencyService.removeDependency(10L, 20L))
        .isInstanceOf(NotFoundException.class);
    verify(dependencyRepository, never()).delete(any(TicketDependency.class));
  }
}
