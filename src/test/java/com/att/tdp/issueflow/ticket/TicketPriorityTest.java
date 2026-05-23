package com.att.tdp.issueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketPriorityTest {

  @Test
  void next_returnsImmediateSuccessorAtEachRung() {
    assertThat(TicketPriority.LOW.next()).isEqualTo(TicketPriority.MEDIUM);
    assertThat(TicketPriority.MEDIUM.next()).isEqualTo(TicketPriority.HIGH);
    assertThat(TicketPriority.HIGH.next()).isEqualTo(TicketPriority.CRITICAL);
  }

  @Test
  void next_returnsNullForCriticalBecauseItIsTheCeiling() {
    assertThat(TicketPriority.CRITICAL.next()).isNull();
  }

  @Test
  void isMax_isTrueOnlyForCritical() {
    assertThat(TicketPriority.LOW.isMax()).isFalse();
    assertThat(TicketPriority.MEDIUM.isMax()).isFalse();
    assertThat(TicketPriority.HIGH.isMax()).isFalse();
    assertThat(TicketPriority.CRITICAL.isMax()).isTrue();
  }
}
