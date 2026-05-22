package com.att.tdp.issueflow.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import com.att.tdp.issueflow.auditlog.dto.AuditLogFilter;
import com.att.tdp.issueflow.auditlog.dto.AuditLogResponse;
import com.att.tdp.issueflow.common.error.InvalidFilterException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

  @Mock private AuditLogRepository auditLogRepository;
  @Mock private UserRepository userRepository;
  @Mock private AuditLogMapper auditLogMapper;

  @InjectMocks private AuditLogService auditLogService;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // ── record() tests ────────────────────────────────────────────────────────

  @Test
  void recordsCreateAuditEntryWithAuthenticatedActor() {
    var userDetails = new User("jdoe", "", List.of());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, List.of()));

    com.att.tdp.issueflow.user.User dbUser = new com.att.tdp.issueflow.user.User();
    dbUser.setId(7L);
    when(userRepository.findByUsernameIgnoreCase("jdoe")).thenReturn(Optional.of(dbUser));

    Ticket ticket = new Ticket();
    ticket.setId(42L);

    auditLogService.record(AuditAction.CREATE, ticket);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(captor.capture());
    AuditLog saved = captor.getValue();

    assertThat(saved.getAction()).isEqualTo(AuditAction.CREATE);
    assertThat(saved.getEntityType()).isEqualTo(AuditEntityType.TICKET);
    assertThat(saved.getEntityId()).isEqualTo(42L);
    assertThat(saved.getPerformedBy()).isEqualTo(7L);
    assertThat(saved.getActor()).isEqualTo(ActorKind.USER);
    assertThat(saved.getTimestamp()).isBetween(Instant.now().minusSeconds(5), Instant.now());
  }

  @Test
  void recordsCreateAuditEntryWithSystemActorWhenNoAuthentication() {
    SecurityContextHolder.clearContext();

    Ticket ticket = new Ticket();
    ticket.setId(42L);

    auditLogService.record(AuditAction.CREATE, ticket);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(captor.capture());
    AuditLog saved = captor.getValue();

    assertThat(saved.getPerformedBy()).isNull();
    assertThat(saved.getActor()).isEqualTo(ActorKind.SYSTEM);
  }

  @Test
  void recordsCreateAuditEntryWithSystemActorWhenUsernameNotFoundInDatabase() {
    var userDetails = new User("ghost", "", List.of());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, List.of()));
    when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

    Ticket ticket = new Ticket();
    ticket.setId(42L);

    auditLogService.record(AuditAction.CREATE, ticket);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(captor.capture());
    AuditLog saved = captor.getValue();

    assertThat(saved.getPerformedBy()).isNull();
    assertThat(saved.getActor()).isEqualTo(ActorKind.SYSTEM);
  }

  // ── findAll() tests ───────────────────────────────────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void findAllReturnsEntriesSortedByTimestampDescending() {
    AuditLog entry1 = new AuditLog();
    entry1.setId(1L);
    AuditLog entry2 = new AuditLog();
    entry2.setId(2L);
    AuditLog entry3 = new AuditLog();
    entry3.setId(3L);

    List<AuditLog> repoResult = List.of(entry1, entry2, entry3);
    when(auditLogRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(repoResult);

    AuditLogResponse r1 = new AuditLogResponse(1L, null, null, null, null, null, null);
    AuditLogResponse r2 = new AuditLogResponse(2L, null, null, null, null, null, null);
    AuditLogResponse r3 = new AuditLogResponse(3L, null, null, null, null, null, null);
    when(auditLogMapper.toResponseList(repoResult)).thenReturn(List.of(r1, r2, r3));

    AuditLogFilter filter = new AuditLogFilter(null, null, null, null);
    List<AuditLogResponse> result = auditLogService.findAll(filter);

    ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
    verify(auditLogRepository).findAll(any(Specification.class), sortCaptor.capture());
    Sort sort = sortCaptor.getValue();
    assertThat(sort.getOrderFor("timestamp")).isNotNull();
    assertThat(sort.getOrderFor("timestamp").isDescending()).isTrue();

    assertThat(result).containsExactly(r1, r2, r3);
  }

  // ── filter validation tests ───────────────────────────────────────────────

  @Test
  void findAllAppliesEntityTypeFilter() {
    when(auditLogRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of());
    when(auditLogMapper.toResponseList(List.of())).thenReturn(List.of());

    AuditLogFilter filter = new AuditLogFilter(AuditEntityType.TICKET, null, null, null);
    auditLogService.findAll(filter);

    verify(auditLogRepository).findAll(any(Specification.class), any(Sort.class));
  }

  @Test
  void findAllReturnsEmptyListWhenFiltersMatchNothing() {
    when(auditLogRepository.findAll(any(Specification.class), any(Sort.class)))
        .thenReturn(List.of());
    when(auditLogMapper.toResponseList(List.of())).thenReturn(List.of());

    AuditLogFilter filter =
        new AuditLogFilter(
            AuditEntityType.TICKET, 999_999_999L, AuditAction.DELETE, ActorKind.SYSTEM);
    List<AuditLogResponse> result = auditLogService.findAll(filter);

    assertThat(result).isEmpty();
  }

  @Test
  void validateThrowsWhenEntityIdSuppliedWithoutEntityType() {
    AuditLogFilter filter = new AuditLogFilter(null, 5L, null, null);

    assertThatThrownBy(() -> auditLogService.findAll(filter))
        .isInstanceOf(InvalidFilterException.class)
        .hasMessageContaining("entityType");
  }
}
