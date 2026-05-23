package com.att.tdp.issueflow.ticket.escalation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.att.tdp.issueflow.auditlog.AuditAction;
import com.att.tdp.issueflow.auditlog.AuditContext;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketStatus;

@ExtendWith(MockitoExtension.class)
class EscalationServiceTest {

  @Mock private TicketRepository ticketRepository;

  private EscalationService escalationService;

  @BeforeEach
  void setup() {
    // The service self-injects itself for transactional dispatch in production. In tests we wire
    // it as its own collaborator so runEscalation() invokes the real escalateOne() method.
    escalationService = new EscalationService(ticketRepository, null);
    escalationService = new EscalationService(ticketRepository, escalationService);
  }

  private Ticket overdueTicket(TicketPriority priority) {
    Ticket ticket = new Ticket();
    ticket.setId(42L);
    ticket.setStatus(TicketStatus.IN_PROGRESS);
    ticket.setPriority(priority);
    ticket.setDueDate(Instant.now().minusSeconds(3600));
    return ticket;
  }

  // ---------------- escalateOne: bump ladder ----------------

  @Test
  void escalateOne_bumpsLowOverdueTicketToMedium() {
    Ticket ticket = overdueTicket(TicketPriority.LOW);
    when(ticketRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(ticket));

    escalationService.escalateOne(42L);

    assertThat(ticket.getPriority()).isEqualTo(TicketPriority.MEDIUM);
    verify(ticketRepository).save(ticket);
  }

  @Test
  void escalateOne_bumpsMediumOverdueTicketToHigh() {
    Ticket ticket = overdueTicket(TicketPriority.MEDIUM);
    when(ticketRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(ticket));

    escalationService.escalateOne(42L);

    assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HIGH);
    verify(ticketRepository).save(ticket);
  }

  @Test
  void escalateOne_bumpsHighOverdueTicketToCritical() {
    Ticket ticket = overdueTicket(TicketPriority.HIGH);
    when(ticketRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(ticket));

    escalationService.escalateOne(42L);

    assertThat(ticket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
    verify(ticketRepository).save(ticket);
  }

  // ---------------- escalateOne: CRITICAL is the ceiling ----------------

  @Test
  void escalateOne_isNoOpWhenAlreadyCritical() {
    Ticket ticket = overdueTicket(TicketPriority.CRITICAL);
    when(ticketRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(ticket));

    escalationService.escalateOne(42L);

    assertThat(ticket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
    verify(ticketRepository, never()).save(any());
  }

  // ---------------- escalateOne: defensive guards ----------------

  @Test
  void escalateOne_skipsTicketWithNullDueDate() {
    Ticket ticket = overdueTicket(TicketPriority.LOW);
    ticket.setDueDate(null);
    when(ticketRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(ticket));

    escalationService.escalateOne(42L);

    verify(ticketRepository, never()).save(any());
  }

  @Test
  void escalateOne_skipsTicketWhoseDueDateIsInTheFuture() {
    Ticket ticket = overdueTicket(TicketPriority.LOW);
    ticket.setDueDate(Instant.now().plusSeconds(3600));
    when(ticketRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(ticket));

    escalationService.escalateOne(42L);

    verify(ticketRepository, never()).save(any());
  }

  @Test
  void escalateOne_skipsTerminalTicket() {
    Ticket ticket = overdueTicket(TicketPriority.LOW);
    ticket.setStatus(TicketStatus.DONE);
    when(ticketRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(ticket));

    escalationService.escalateOne(42L);

    verify(ticketRepository, never()).save(any());
  }

  @Test
  void escalateOne_skipsMissingTicket() {
    when(ticketRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.empty());

    escalationService.escalateOne(42L);

    verify(ticketRepository, never()).save(any());
  }

  // ---------------- audit hint ----------------

  @Test
  void escalateOne_hintsAutoEscalateBeforeSaveOnBump() {
    Ticket ticket = overdueTicket(TicketPriority.LOW);
    when(ticketRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(ticket));

    ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
    when(ticketRepository.save(saved.capture()))
        .thenAnswer(
            invocation -> {
              // Consume the hint mid-save to verify it was set immediately beforehand.
              AuditAction observed = AuditContext.consumeOrDefault(AuditAction.UPDATE);
              assertThat(observed).isEqualTo(AuditAction.AUTO_ESCALATE);
              return invocation.getArgument(0);
            });

    escalationService.escalateOne(42L);

    assertThat(saved.getValue()).isSameAs(ticket);
  }

  // ---------------- runEscalation: optimistic-lock isolation ----------------

  @Test
  void runEscalation_skipsTicketThatThrowsOptimisticLockButContinuesOthers() {
    Ticket first = overdueTicket(TicketPriority.LOW);
    first.setId(1L);
    Ticket second = overdueTicket(TicketPriority.HIGH);
    second.setId(2L);

    when(ticketRepository.findIdsOfOverdueNonDoneActiveTickets(any(Instant.class)))
        .thenReturn(List.of(1L, 2L));
    when(ticketRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(first));
    when(ticketRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(second));
    doThrow(new ObjectOptimisticLockingFailureException(Ticket.class, 1L))
        .when(ticketRepository)
        .save(first);

    escalationService.runEscalation();

    verify(ticketRepository, times(1)).save(first);
    verify(ticketRepository, times(1)).save(second);
    assertThat(second.getPriority()).isEqualTo(TicketPriority.CRITICAL);
  }
}
