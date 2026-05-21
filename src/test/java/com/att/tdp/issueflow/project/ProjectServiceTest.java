package com.att.tdp.issueflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.att.tdp.issueflow.common.error.DuplicateResourceException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.project.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

  @Mock private ProjectRepository projectRepository;
  @Mock private UserRepository userRepository;
  @Mock private ProjectMapper projectMapper;

  @InjectMocks private ProjectService projectService;

  private User owner;
  private Project activeProject;
  private ProjectResponse response;
  private CreateProjectRequest createRequest;

  @BeforeEach
  void setup() {
    owner = new User();
    owner.setId(1L);

    activeProject = new Project();
    activeProject.setId(10L);
    activeProject.setName("Backend Rewrite");
    activeProject.setDescription("Modernise the service layer");
    activeProject.setOwner(owner);

    response = new ProjectResponse(10L, "Backend Rewrite", "Modernise the service layer", 1L);
    createRequest = new CreateProjectRequest("Backend Rewrite", "Modernise the service layer", 1L);
  }

  // ---------------- create ----------------

  @Test
  void createProject_persistsAndReturnsResponse() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
    when(projectRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Backend Rewrite"))
        .thenReturn(false);
    when(projectMapper.toEntity(createRequest, owner)).thenReturn(activeProject);
    when(projectRepository.save(activeProject)).thenReturn(activeProject);
    when(projectMapper.toResponse(activeProject)).thenReturn(response);

    ProjectResponse result = projectService.create(createRequest);

    assertThat(result).isEqualTo(response);
    verify(projectRepository).save(activeProject);
  }

  @Test
  void createProject_throwsDuplicateWhenNameExists() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
    when(projectRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Backend Rewrite"))
        .thenReturn(true);

    assertThatThrownBy(() -> projectService.create(createRequest))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessageContaining("name")
        .hasMessageContaining("Backend Rewrite");
    verify(projectRepository, never()).save(any());
  }

  @Test
  void createProject_throwsNotFoundWhenOwnerAbsent() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectService.create(createRequest))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("1");
    verify(projectRepository, never()).save(any());
  }

  // ---------------- getById ----------------

  @Test
  void getById_returnsProjectWhenActive() {
    when(projectRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeProject));
    when(projectMapper.toResponse(activeProject)).thenReturn(response);

    ProjectResponse result = projectService.getById(10L);

    assertThat(result).isEqualTo(response);
  }

  @Test
  void getById_throwsNotFoundWhenSoftDeleted() {
    when(projectRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectService.getById(10L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Project")
        .hasMessageContaining("10");
  }

  // ---------------- list ----------------

  @Test
  void list_excludesSoftDeletedProjects() {
    when(projectRepository.findAllByDeletedAtIsNullOrderByIdAsc())
        .thenReturn(List.of(activeProject));
    when(projectMapper.toResponse(activeProject)).thenReturn(response);

    List<ProjectResponse> result = projectService.list();

    assertThat(result).containsExactly(response);
  }

  // ---------------- update ----------------

  @Test
  void update_appliesNameChange() {
    when(projectRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeProject));

    projectService.update(10L, new UpdateProjectRequest("New Name", null));

    assertThat(activeProject.getName()).isEqualTo("New Name");
    assertThat(activeProject.getDescription()).isEqualTo("Modernise the service layer");
    verify(projectRepository).save(activeProject);
  }

  @Test
  void update_throwsNotFoundWhenSoftDeleted() {
    when(projectRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectService.update(10L, new UpdateProjectRequest("New Name", null)))
        .isInstanceOf(NotFoundException.class);
    verify(projectRepository, never()).save(any());
  }

  // ---------------- softDelete ----------------

  @Test
  void softDelete_setsDeletedAt() {
    when(projectRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeProject));

    projectService.softDelete(10L);

    assertThat(activeProject.getDeletedAt()).isNotNull();
    assertThat(activeProject.getDeletedAt()).isBeforeOrEqualTo(Instant.now());
    verify(projectRepository).save(activeProject);
  }

  @Test
  void softDelete_throwsNotFoundOnAlreadyDeleted() {
    when(projectRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectService.softDelete(10L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Project")
        .hasMessageContaining("10");
    verify(projectRepository, never()).save(any());
  }
}
