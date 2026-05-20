package com.att.tdp.issueflow.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a single {@link Clock} bean so that time-dependent code (the health endpoint's timestamp,
 * future schedulers) can be substituted with a fixed clock in tests.
 */
@Configuration
public class ClockConfiguration {

  /**
   * Provides the wall-clock UTC clock used by production code paths.
   *
   * @return a system UTC {@link Clock}
   */
  @Bean
  public Clock systemClock() {
    return Clock.systemUTC();
  }
}
