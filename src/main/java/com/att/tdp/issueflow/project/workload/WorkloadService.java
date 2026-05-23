package com.att.tdp.issueflow.project.workload;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.project.workload.dto.WorkloadResponse;
import com.att.tdp.issueflow.user.UserRepository;

/**
 * Business logic for the project-workload surface. Aggregates open-ticket counts across developers
 * in a project so callers (and the auto-assignment path) can reason about load distribution.
 */
@Service
public class WorkloadService {

  private final UserRepository userRepository;
  private final ProjectRepository projectRepository;

  public WorkloadService(UserRepository userRepository, ProjectRepository projectRepository) {
    this.userRepository = userRepository;
    this.projectRepository = projectRepository;
  }

  /**
   * Returns the workload distribution for every developer eligible for assignment in the given
   * project, ordered ascending by open-ticket count.
   *
   * @param projectId the project identifier
   * @return one row per developer, sorted least-busy first
   * @throws NotFoundException if no active project has that id
   */
  @Transactional(readOnly = true)
  public List<WorkloadResponse> getWorkload(Long projectId) {
    projectRepository
        .findByIdAndDeletedAtIsNull(projectId)
        .orElseThrow(() -> new NotFoundException("Project", projectId));

    return userRepository.findWorkloadByProjectId(projectId).stream()
        .map(
            row ->
                new WorkloadResponse(row.getUserId(), row.getUsername(), row.getOpenTicketCount()))
        .toList();
  }
}
