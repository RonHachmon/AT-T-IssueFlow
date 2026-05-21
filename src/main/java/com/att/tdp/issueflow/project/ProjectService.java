package com.att.tdp.issueflow.project;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.common.error.DuplicateResourceException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.project.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;

/**
 * Business logic for the project-management surface. All HTTP-status mapping happens outside this
 * class — the service throws semantic exceptions ({@link NotFoundException}, {@link
 * DuplicateResourceException}) and the {@code @RestControllerAdvice} maps them to status codes.
 *
 * <p>Every read uses {@code findByIdAndDeletedAtIsNull} so soft-deleted projects are invisible
 * without any additional filtering at the call site.
 */
@Service
public class ProjectService {

  private static final String RESOURCE = "Project";
  private static final String FIELD_NAME = "name";

  private final ProjectRepository projectRepository;
  private final UserRepository userRepository;
  private final ProjectMapper projectMapper;

  public ProjectService(
      ProjectRepository projectRepository,
      UserRepository userRepository,
      ProjectMapper projectMapper) {
    this.projectRepository = projectRepository;
    this.userRepository = userRepository;
    this.projectMapper = projectMapper;
  }

  /**
   * Creates a new project. Resolves the owner, enforces case-insensitive name uniqueness among
   * active projects, maps the request to an entity, and returns the persisted response.
   *
   * @param request the validated create request
   * @return the persisted project as a response DTO
   * @throws NotFoundException if no user exists with the given {@code ownerId}
   * @throws DuplicateResourceException if an active project with the same name already exists
   */
  @Transactional
  public ProjectResponse create(CreateProjectRequest request) {
    User owner =
        userRepository
            .findById(request.ownerId())
            .orElseThrow(() -> new NotFoundException("User", request.ownerId()));

    if (projectRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(request.name())) {
      throw new DuplicateResourceException(FIELD_NAME, request.name());
    }

    Project project = projectMapper.toEntity(request, owner);
    Project saved = projectRepository.save(project);
    return projectMapper.toResponse(saved);
  }

  /**
   * Fetches a single active project by id.
   *
   * @param id the project identifier
   * @return the project as a response DTO
   * @throws NotFoundException if no active project has that id
   */
  @Transactional(readOnly = true)
  public ProjectResponse getById(Long id) {
    return projectRepository
        .findByIdAndDeletedAtIsNull(id)
        .map(projectMapper::toResponse)
        .orElseThrow(() -> new NotFoundException(RESOURCE, id));
  }

  /**
   * Returns all active projects ordered by id ascending. Soft-deleted projects are excluded.
   *
   * @return all active projects
   */
  @Transactional(readOnly = true)
  public List<ProjectResponse> list() {
    return projectRepository.findAllByDeletedAtIsNullOrderByIdAsc().stream()
        .map(projectMapper::toResponse)
        .toList();
  }

  /**
   * Applies a partial update to an active project's {@code name} and/or {@code description}. At
   * least one field must be non-null (enforced upstream by DTO validation).
   *
   * @param id the project identifier
   * @param request the partial update
   * @throws NotFoundException if no active project has that id
   */
  @Transactional
  public void update(Long id, UpdateProjectRequest request) {
    Project project =
        projectRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(RESOURCE, id));

    if (request.name() != null) {
      project.setName(request.name());
    }
    if (request.description() != null) {
      project.setDescription(request.description());
    }
    projectRepository.save(project);
  }

  /**
   * Soft-deletes a project by setting its {@code deletedAt} timestamp. Subsequent reads via {@link
   * #getById} or {@link #list} will exclude this project. Calling soft-delete on an already-deleted
   * project raises {@link NotFoundException} because {@code findByIdAndDeletedAtIsNull} returns
   * empty.
   *
   * @param id the project identifier
   * @throws NotFoundException if no active project has that id, or if the project is already
   *     deleted
   */
  @Transactional
  public void softDelete(Long id) {
    Project project =
        projectRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(RESOURCE, id));

    project.setDeletedAt(Instant.now());
    projectRepository.save(project);
  }
}
