package com.att.tdp.issueflow.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuditContextTest {

  @AfterEach
  void cleanup() {
    AuditContext.consumeOrDefault(null);
  }

  @Test
  void consumeReturnsFallbackWhenNoHintSet() {
    AuditAction result = AuditContext.consumeOrDefault(AuditAction.UPDATE);

    assertThat(result).isEqualTo(AuditAction.UPDATE);
  }

  @Test
  void consumeReturnsHintedActionAndClearsIt() {
    AuditContext.hint(AuditAction.SOFT_DELETE);

    AuditAction first = AuditContext.consumeOrDefault(AuditAction.UPDATE);
    AuditAction second = AuditContext.consumeOrDefault(AuditAction.UPDATE);

    assertThat(first).isEqualTo(AuditAction.SOFT_DELETE);
    assertThat(second).isEqualTo(AuditAction.UPDATE);
  }

  @Test
  void hintIsThreadScopedAndDoesNotLeakAcrossThreads() throws Exception {
    AuditContext.hint(AuditAction.SOFT_DELETE);

    AuditAction otherThreadResult =
        CompletableFuture.supplyAsync(() -> AuditContext.consumeOrDefault(AuditAction.UPDATE))
            .get();

    assertThat(otherThreadResult).isEqualTo(AuditAction.UPDATE);
    assertThat(AuditContext.consumeOrDefault(AuditAction.CREATE))
        .isEqualTo(AuditAction.SOFT_DELETE);
  }
}
