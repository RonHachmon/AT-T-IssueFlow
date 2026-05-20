package com.att.tdp.issueflow.common.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;

import com.att.tdp.issueflow.common.health.HealthResponse.ComponentStatus;

class HealthControllerTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-05-20T10:42:13.512Z");
  private final Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

  @Test
  void returnsTwoHundredAndUpWhenAllComponentsHealthy() {
    HealthEndpoint healthEndpoint = mock(HealthEndpoint.class);
    when(healthEndpoint.health()).thenReturn(Health.up().build());
    HealthController controller = new HealthController(healthEndpoint, fixedClock);

    ResponseEntity<HealthResponse> response = controller.health();

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    HealthResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo("UP");
    assertThat(body.timestamp()).isEqualTo(FIXED_INSTANT);

    ComponentStatus application = body.components().get("application");
    assertThat(application.status()).isEqualTo("UP");
    assertThat(application.detail()).isEmpty();

    ComponentStatus db = body.components().get("db");
    assertThat(db.status()).isEqualTo("UP");
    assertThat(db.detail()).isEmpty();
  }

  @Test
  void returnsFiveHundredThreeAndDownWhenDatabaseUnreachable() {
    HealthEndpoint healthEndpoint = mock(HealthEndpoint.class);
    Health downHealth =
        Health.down().withDetail("error", "Connection refused: localhost:5432").build();
    when(healthEndpoint.health()).thenReturn(downHealth);
    HealthController controller = new HealthController(healthEndpoint, fixedClock);

    ResponseEntity<HealthResponse> response = controller.health();

    assertThat(response.getStatusCode().value()).isEqualTo(503);
    HealthResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo("DOWN");

    ComponentStatus application = body.components().get("application");
    assertThat(application.status()).isEqualTo("UP");

    ComponentStatus db = body.components().get("db");
    assertThat(db.status()).isEqualTo("DOWN");
    assertThat(db.detail()).contains("Connection refused");
  }
}
