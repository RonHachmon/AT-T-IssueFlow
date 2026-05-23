package com.att.tdp.issueflow.project.workload;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.project.workload.dto.WorkloadResponse;

/**
 * REST endpoint for project workload analytics. Provides read-only access to developer assignment
 * metrics.
 */
@RestController
@RequestMapping("/projects/{projectId}/workload")
public class WorkloadController {

  private final WorkloadService workloadService;

  public WorkloadController(WorkloadService workloadService) {
    this.workloadService = workloadService;
  }

  /**
   * Fetches the current workload distribution for all developers in the project.
   *
   * @param projectId the project identifier
   * @return a list of {@link WorkloadResponse} objects, sorted by workload ascending
   */
  @GetMapping
  public List<WorkloadResponse> getProjectWorkload(@PathVariable Long projectId) {
    return workloadService.getWorkload(projectId);
  }
}
