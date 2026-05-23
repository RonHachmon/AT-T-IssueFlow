package com.att.tdp.issueflow.project.workload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.project.workload.dto.WorkloadResponse;
import com.att.tdp.issueflow.user.UserRepository;
import com.att.tdp.issueflow.user.WorkloadRow;

@ExtendWith(MockitoExtension.class)
class WorkloadServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private ProjectRepository projectRepository;

  @InjectMocks private WorkloadService workloadService;

  private Project activeProject;

  @BeforeEach
  void setup() {
    activeProject = new Project();
    activeProject.setId(10L);
    activeProject.setName("Backend Rewrite");
  }

  // ---------------- getWorkload: happy path ----------------

  @Test
  void getWorkload_returnsRowsMappedFromRepository() {
    when(projectRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeProject));
    when(userRepository.findWorkloadByProjectId(10L))
        .thenReturn(List.of(row(1L, "alice", 0L), row(2L, "bob", 3L)));

    List<WorkloadResponse> result = workloadService.getWorkload(10L);

    assertThat(result)
        .containsExactly(
            new WorkloadResponse(1L, "alice", 0L), new WorkloadResponse(2L, "bob", 3L));
  }

  @Test
  void getWorkload_preservesRepositoryOrdering() {
    when(projectRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeProject));
    when(userRepository.findWorkloadByProjectId(10L))
        .thenReturn(List.of(row(2L, "bob", 1L), row(1L, "alice", 4L), row(3L, "carol", 9L)));

    List<WorkloadResponse> result = workloadService.getWorkload(10L);

    assertThat(result).extracting(WorkloadResponse::userId).containsExactly(2L, 1L, 3L);
  }

  @Test
  void getWorkload_returnsEmptyListWhenNoDevelopers() {
    when(projectRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(activeProject));
    when(userRepository.findWorkloadByProjectId(10L)).thenReturn(List.of());

    List<WorkloadResponse> result = workloadService.getWorkload(10L);

    assertThat(result).isEmpty();
  }

  // ---------------- getWorkload: project lookup ----------------

  @Test
  void getWorkload_throwsNotFoundWhenProjectMissing() {
    when(projectRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> workloadService.getWorkload(99L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Project")
        .hasMessageContaining("99");
    verify(userRepository, never()).findWorkloadByProjectId(99L);
  }

  @Test
  void getWorkload_throwsNotFoundWhenProjectSoftDeleted() {
    when(projectRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> workloadService.getWorkload(10L))
        .isInstanceOf(NotFoundException.class);
    verify(userRepository, never()).findWorkloadByProjectId(10L);
  }

  private static WorkloadRow row(Long userId, String username, Long openTicketCount) {
    return new WorkloadRow() {
      @Override
      public Long getUserId() {
        return userId;
      }

      @Override
      public String getUsername() {
        return username;
      }

      @Override
      public Long getOpenTicketCount() {
        return openTicketCount;
      }
    };
  }
}
