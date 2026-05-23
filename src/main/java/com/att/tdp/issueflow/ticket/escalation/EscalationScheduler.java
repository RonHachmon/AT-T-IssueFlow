package com.att.tdp.issueflow.ticket.escalation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron entry point for the auto-escalation pass. Bean creation is gated on {@code
 * app.escalation.enabled=true} so tests and other environments can opt out without removing the
 * {@code @EnableScheduling} switch.
 *
 * <p>The schedule is bound to {@code app.escalation.cron}; {@link EscalationProperties} provides
 * the canonical default if the property is omitted, and this annotation falls back to the same
 * literal so the bean is wirable even before properties bind.
 */
@Component
@ConditionalOnProperty(name = "app.escalation.enabled", havingValue = "true", matchIfMissing = true)
public class EscalationScheduler {

  private final EscalationService escalationService;

  public EscalationScheduler(EscalationService escalationService) {
    this.escalationService = escalationService;
  }

  /** Cron-driven trigger that delegates to {@link EscalationService#runEscalation()}. */
  @Scheduled(cron = "${app.escalation.cron:0 0 * * * *}")
  public void runScheduledEscalation() {
    escalationService.runEscalation();
  }
}
