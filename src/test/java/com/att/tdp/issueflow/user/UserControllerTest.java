package com.att.tdp.issueflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.att.tdp.issueflow.user.dto.UpdateUserRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

  @Mock private UserService userService;
  @InjectMocks private UserController userController;

  private final UserResponse response =
      new UserResponse(42L, "alice", "alice@example.com", "Alice Doe", Role.DEVELOPER);

  @Test
  void returnsPersistedUserWhenCreated() {
    CreateUserRequest request =
        new CreateUserRequest("alice", "alice@example.com", "Alice Doe", Role.DEVELOPER);
    when(userService.create(request)).thenReturn(response);

    UserResponse result = userController.create(request);

    assertThat(result).isEqualTo(response);
  }

  @Test
  void returnsUserResponseWhenGetByIdSucceeds() {
    when(userService.getById(42L)).thenReturn(response);

    UserResponse result = userController.getById(42L);

    assertThat(result).isEqualTo(response);
  }

  @Test
  void returnsPlainArrayWhenListSucceeds() {
    List<UserResponse> page = List.of(response);
    when(userService.list()).thenReturn(page);

    List<UserResponse> result = userController.list();

    assertThat(result).containsExactly(response);
  }

  @Test
  void delegatesToServiceWhenUpdateSucceeds() {
    UpdateUserRequest request = new UpdateUserRequest("Alice Smith", Role.ADMIN);

    userController.update(42L, request);

    verify(userService, times(1)).update(42L, request);
  }

  @Test
  void delegatesToServiceWhenDeleteSucceeds() {
    userController.delete(42L);

    verify(userService, times(1)).delete(42L);
  }
}
