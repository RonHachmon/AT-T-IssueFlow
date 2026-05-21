package com.att.tdp.issueflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.att.tdp.issueflow.common.error.DuplicateResourceException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.att.tdp.issueflow.user.dto.UpdateUserRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private UserMapper userMapper;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private UserService userService;

  private CreateUserRequest validCreateRequest;
  private User entity;
  private UserResponse response;

  @BeforeEach
  void setup() {
    validCreateRequest =
        new CreateUserRequest("alice", "alice@example.com", "Alice Doe", Role.DEVELOPER);
    entity = new User();
    entity.setId(42L);
    entity.setUsername("alice");
    entity.setEmail("alice@example.com");
    entity.setFullName("Alice Doe");
    entity.setRole(Role.DEVELOPER);
    response = new UserResponse(42L, "alice", "alice@example.com", "Alice Doe", Role.DEVELOPER);
  }

  // ---------------- create ----------------

  @Test
  void createsUserAndReturnsMappedResponseWhenInputIsValid() {
    when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(false);
    when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
    when(userMapper.toEntity(validCreateRequest)).thenReturn(entity);
    when(userRepository.save(entity)).thenReturn(entity);
    when(userMapper.toResponse(entity)).thenReturn(response);

    UserResponse result = userService.create(validCreateRequest);

    assertThat(result).isEqualTo(response);
    verify(userRepository).save(entity);
  }

  @Test
  void throwsDuplicateResourceExceptionWhenUsernameAlreadyExists() {
    when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(true);

    assertThatThrownBy(() -> userService.create(validCreateRequest))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessageContaining("username")
        .hasMessageContaining("alice");
    verify(userRepository, never()).save(any());
  }

  @Test
  void throwsDuplicateResourceExceptionWhenEmailAlreadyExists() {
    when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(false);
    when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

    assertThatThrownBy(() -> userService.create(validCreateRequest))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessageContaining("email")
        .hasMessageContaining("alice@example.com");
    verify(userRepository, never()).save(any());
  }

  @Test
  void throwsDuplicateResourceExceptionWhenSaveRaisesDataIntegrityViolation() {
    when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(false);
    when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
    when(userMapper.toEntity(validCreateRequest)).thenReturn(entity);
    when(userRepository.save(entity)).thenThrow(new DataIntegrityViolationException("race"));

    assertThatThrownBy(() -> userService.create(validCreateRequest))
        .isInstanceOf(DuplicateResourceException.class);
  }

  // ---------------- getById ----------------

  @Test
  void returnsUserResponseWhenIdExists() {
    when(userRepository.findById(42L)).thenReturn(Optional.of(entity));
    when(userMapper.toResponse(entity)).thenReturn(response);

    UserResponse result = userService.getById(42L);

    assertThat(result).isEqualTo(response);
  }

  @Test
  void throwsNotFoundExceptionWhenGetByIdMisses() {
    when(userRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getById(999L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("User")
        .hasMessageContaining("999");
  }

  // ---------------- list ----------------

  @Test
  void returnsEmptyListWhenNoUsersExist() {
    when(userRepository.findAll(any(Sort.class))).thenReturn(List.of());

    List<UserResponse> result = userService.list();

    assertThat(result).isEmpty();
  }

  @Test
  void returnsAllUsersAsPlainArrayOrderedByIdAscending() {
    when(userRepository.findAll(any(Sort.class))).thenReturn(List.of(entity));
    when(userMapper.toResponse(entity)).thenReturn(response);

    List<UserResponse> result = userService.list();

    assertThat(result).containsExactly(response);
  }

  // ---------------- update ----------------

  @Test
  void updatesOnlyFullNameWhenRoleIsNull() {
    when(userRepository.findById(42L)).thenReturn(Optional.of(entity));

    userService.update(42L, new UpdateUserRequest("Alice Smith", null));

    assertThat(entity.getFullName()).isEqualTo("Alice Smith");
    assertThat(entity.getRole()).isEqualTo(Role.DEVELOPER);
    verify(userRepository).save(entity);
  }

  @Test
  void updatesOnlyRoleWhenFullNameIsNull() {
    when(userRepository.findById(42L)).thenReturn(Optional.of(entity));

    userService.update(42L, new UpdateUserRequest(null, Role.ADMIN));

    assertThat(entity.getFullName()).isEqualTo("Alice Doe");
    assertThat(entity.getRole()).isEqualTo(Role.ADMIN);
  }

  @Test
  void updatesBothFieldsWhenBothProvided() {
    when(userRepository.findById(42L)).thenReturn(Optional.of(entity));

    userService.update(42L, new UpdateUserRequest("Alice Smith", Role.ADMIN));

    assertThat(entity.getFullName()).isEqualTo("Alice Smith");
    assertThat(entity.getRole()).isEqualTo(Role.ADMIN);
  }

  @Test
  void throwsNotFoundExceptionWhenUpdateTargetMissing() {
    when(userRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.update(999L, new UpdateUserRequest("X", null)))
        .isInstanceOf(NotFoundException.class);
    verify(userRepository, never()).save(any());
  }

  // ---------------- delete ----------------

  @Test
  void deletesUserWhenIdExists() {
    when(userRepository.existsById(42L)).thenReturn(true);

    userService.delete(42L);

    verify(userRepository, times(1)).deleteById(42L);
  }

  @Test
  void throwsNotFoundExceptionWhenDeleteTargetMissing() {
    when(userRepository.existsById(999L)).thenReturn(false);

    assertThatThrownBy(() -> userService.delete(999L)).isInstanceOf(NotFoundException.class);
    verify(userRepository, never()).deleteById(any());
  }
}
