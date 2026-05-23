package com.att.tdp.issueflow.project;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.project.dto.UpdateProjectRequest;

/**
 * REST controller for the project-management surface. The endpoint contract is fixed by the
 * project's {@code README.md} Projects APIs table — every verb, path, and status code matches that
 * table one-to-one. All five endpoints return {@code 200 OK} per the README canonical template.
 * Validation triggering lives here; business logic lives in {@link ProjectService}.
 */
@RestController
@RequestMapping("/projects")
public class ProjectController {

  private final ProjectService projectService;

  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
  }

  /**
   * Creates a new project.
   *
   * @param request the validated request body
   * @return {@code 200 OK} with the persisted project
   */
  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
    return projectService.create(request);
  }

  /**
   * Returns all active projects as a plain JSON array.
   *
   * @return {@code 200 OK} with all active projects
   */
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public List<ProjectResponse> list() {
    return projectService.list();
  }

  /**
   * Fetches a single active project by id.
   *
   * @param projectId the project identifier
   * @return {@code 200 OK} with the project; {@code 404} via global advice if the id is unknown or
   *     soft-deleted
   */
  @GetMapping("/{projectId}")
  @ResponseStatus(HttpStatus.OK)
  public ProjectResponse getById(@PathVariable Long projectId) {
    return projectService.getById(projectId);
  }

  /**
   * Partially updates a project's {@code name} and/or {@code description}. Returns {@code 200 OK}
   * with no body.
   *
   * @param projectId the project identifier
   * @param request the validated partial update
   */
  @PatchMapping("/{projectId}")
  @ResponseStatus(HttpStatus.OK)
  public void update(
      @PathVariable Long projectId, @Valid @RequestBody UpdateProjectRequest request) {
    projectService.update(projectId, request);
  }

  /**
   * Soft-deletes a project. Returns {@code 200 OK} with no body. A second call with the same id
   * returns {@code 404} because the project is no longer visible as active.
   *
   * @param projectId the project identifier
   */
  @DeleteMapping("/{projectId}")
  @ResponseStatus(HttpStatus.OK)
  public void softDelete(@PathVariable Long projectId) {
    projectService.softDelete(projectId);
  }

  /**
   * Lists all soft-deleted projects. Admin-only — non-admin callers receive {@code 403}.
   *
   * @return {@code 200 OK} with all soft-deleted projects
   */
  @GetMapping("/deleted")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasAuthority('ADMIN')")
  public List<ProjectResponse> listDeleted() {
    return projectService.listDeleted();
  }

  /**
   * Restores a soft-deleted project by clearing its {@code deletedAt} timestamp. Admin-only —
   * non-admin callers receive {@code 403}. Returns {@code 200 OK} with no body. Restoring a project
   * that is already active (or unknown) returns {@code 404}.
   *
   * @param projectId the project identifier
   */
  @PostMapping("/{projectId}/restore")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasAuthority('ADMIN')")
  public void restore(@PathVariable Long projectId) {
    projectService.restore(projectId);
  }
}
