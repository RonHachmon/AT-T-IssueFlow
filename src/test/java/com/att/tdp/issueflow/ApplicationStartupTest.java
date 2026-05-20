package com.att.tdp.issueflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * The single Spring-context smoke test allowed by constitution Principle II. Proves the application
 * context loads against the test profile (H2 with PostgreSQL mode) and that {@code GET /health}
 * reports the application as up. Every other test in this project runs on the pure JVM with no
 * Spring context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationStartupTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void applicationContextLoadsAndHealthEndpointReportsUp() {
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(
            "/health",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull().containsEntry("status", "UP");
  }
}
