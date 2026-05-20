package com.att.tdp.issueflow.common.health;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.common.health.HealthResponse.ComponentStatus;

/**
 * Reports the application's liveness and the reachability of its database. Returns {@code 200 OK}
 * when both components are healthy and {@code 503 Service Unavailable} when the database (or any
 * dependency surfaced by Actuator) is down. The endpoint is unauthenticated by design — it is
 * plumbing, not a business resource.
 */
@RestController
public class HealthController {

  private static final String STATUS_UP = "UP";
  private static final String STATUS_DOWN = "DOWN";
  private static final String COMPONENT_APPLICATION = "application";
  private static final String COMPONENT_DB = "db";
  private static final String DETAIL_HEALTHY = "";
  private static final String DETAIL_KEY_ERROR = "error";

  private final HealthEndpoint healthEndpoint;
  private final Clock clock;

  /**
   * Constructs the controller with collaborators provided by the Spring context.
   *
   * @param healthEndpoint Actuator's aggregated health endpoint; supplies the underlying status for
   *     each registered indicator (including the auto-configured database indicator)
   * @param clock injected so tests can use a fixed clock for {@link HealthResponse#timestamp()}
   */
  public HealthController(HealthEndpoint healthEndpoint, Clock clock) {
    this.healthEndpoint = healthEndpoint;
    this.clock = clock;
  }

  /**
   * Samples application + database health and returns the IssueFlow health envelope.
   *
   * @return {@code 200 OK} with status {@code UP} when all components are healthy; {@code 503
   *     Service Unavailable} with status {@code DOWN} otherwise
   */
  @GetMapping("/health")
  public ResponseEntity<HealthResponse> health() {
    HealthComponent root = healthEndpoint.health();
    ComponentStatus applicationStatus = new ComponentStatus(STATUS_UP, DETAIL_HEALTHY);
    ComponentStatus dbStatus = extractComponent(root, COMPONENT_DB);

    Map<String, ComponentStatus> components = new LinkedHashMap<>();
    components.put(COMPONENT_APPLICATION, applicationStatus);
    components.put(COMPONENT_DB, dbStatus);

    boolean overallUp = Status.UP.equals(root.getStatus());
    String overallStatus = overallUp ? STATUS_UP : STATUS_DOWN;
    HttpStatus httpStatus = overallUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

    HealthResponse body = new HealthResponse(overallStatus, components, Instant.now(clock));
    return ResponseEntity.status(httpStatus).body(body);
  }

  private static ComponentStatus extractComponent(HealthComponent root, String name) {
    if (root instanceof CompositeHealth composite) {
      HealthComponent child = composite.getComponents().get(name);
      if (child != null) {
        return toComponentStatus(child);
      }
    }
    return toComponentStatus(root);
  }

  private static ComponentStatus toComponentStatus(HealthComponent component) {
    String status = Status.UP.equals(component.getStatus()) ? STATUS_UP : STATUS_DOWN;
    String detail = DETAIL_HEALTHY;
    if (component instanceof Health health) {
      Object error = health.getDetails().get(DETAIL_KEY_ERROR);
      if (error != null) {
        detail = error.toString();
      }
    }
    return new ComponentStatus(status, detail);
  }
}
