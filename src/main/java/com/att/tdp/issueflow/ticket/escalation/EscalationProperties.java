package com.att.tdp.issueflow.ticket.escalation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for the {@code app.escalation} configuration block, consumed by the auto-escalation
 * scheduler.
 *
 * <p>Register via {@code @EnableConfigurationProperties(EscalationProperties.class)} on the
 * application class.
 *
 * @param cron Spring-cron expression controlling how often the escalation pass runs; defaults to
 *     hourly when omitted or blank
 * @param enabled toggle for the scheduler; when {@code false} the {@code EscalationScheduler} bean
 *     is not registered, which is the default for tests
 */
@ConfigurationProperties(prefix = "app.escalation")
public record EscalationProperties(String cron, boolean enabled) {

  public EscalationProperties {
    if (cron == null || cron.isBlank()) {
      cron = "0 0 * * * *";
    }
  }
}
