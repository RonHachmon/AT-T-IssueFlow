package com.att.tdp.issueflow.auditlog;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.att.tdp.issueflow.common.config.SpringContextHolder;
import com.att.tdp.issueflow.ticket.Ticket;

@ExtendWith(MockitoExtension.class)
class AuditableEntityListenerTest {

  @Mock private AuditLogService auditLogService;

  private final AuditableEntityListener listener = new AuditableEntityListener();
  private final Ticket entity = new Ticket();

  @AfterEach
  void cleanupHint() {
    AuditContext.consumeOrDefault(null);
  }

  @Test
  void onCreateRecordsCreateActionWhenNoHint() {
    try (MockedStatic<SpringContextHolder> holder = Mockito.mockStatic(SpringContextHolder.class)) {
      holder
          .when(() -> SpringContextHolder.bean(AuditLogService.class))
          .thenReturn(auditLogService);

      listener.onCreate(entity);

      verify(auditLogService).record(AuditAction.CREATE, entity);
    }
  }

  @Test
  void onUpdateRecordsUpdateActionWhenNoHint() {
    try (MockedStatic<SpringContextHolder> holder = Mockito.mockStatic(SpringContextHolder.class)) {
      holder
          .when(() -> SpringContextHolder.bean(AuditLogService.class))
          .thenReturn(auditLogService);

      listener.onUpdate(entity);

      verify(auditLogService).record(AuditAction.UPDATE, entity);
    }
  }

  @Test
  void onUpdateRecordsHintedActionWhenServiceSetHint() {
    try (MockedStatic<SpringContextHolder> holder = Mockito.mockStatic(SpringContextHolder.class)) {
      holder
          .when(() -> SpringContextHolder.bean(AuditLogService.class))
          .thenReturn(auditLogService);

      AuditContext.hint(AuditAction.SOFT_DELETE);
      listener.onUpdate(entity);

      verify(auditLogService).record(AuditAction.SOFT_DELETE, entity);
    }
  }

  @Test
  void onRemoveRecordsDeleteAction() {
    try (MockedStatic<SpringContextHolder> holder = Mockito.mockStatic(SpringContextHolder.class)) {
      holder
          .when(() -> SpringContextHolder.bean(AuditLogService.class))
          .thenReturn(auditLogService);

      listener.onRemove(entity);

      verify(auditLogService).record(AuditAction.DELETE, entity);
    }
  }

  @Test
  void consecutiveOnUpdateCallsRevertToDefaultAfterFirstConsumesHint() {
    try (MockedStatic<SpringContextHolder> holder = Mockito.mockStatic(SpringContextHolder.class)) {
      holder
          .when(() -> SpringContextHolder.bean(AuditLogService.class))
          .thenReturn(auditLogService);

      AuditContext.hint(AuditAction.SOFT_DELETE);
      listener.onUpdate(entity);
      listener.onUpdate(entity);

      verify(auditLogService).record(AuditAction.SOFT_DELETE, entity);
      verify(auditLogService).record(AuditAction.UPDATE, entity);
    }
  }
}
