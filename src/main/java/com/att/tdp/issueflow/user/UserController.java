package com.att.tdp.issueflow.user;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.att.tdp.issueflow.user.dto.UpdateUserRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;

/**
 * REST controller for the user-management surface. The endpoint contract is fixed by the project's
 * {@code README.md} Users APIs table — every verb, path, and status code on this class matches that
 * table one-to-one. Validation triggering lives here; business logic lives in {@link UserService}.
 */
@RestController
@RequestMapping("/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  /**
   * Creates a new user.
   *
   * @param request the validated request body
   * @return {@code 200 OK} with the persisted body
   */
  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
    return userService.create(request);
  }

  /**
   * @return all users
   */
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public List<UserResponse> list() {
    return userService.list();
  }

  /**
   * Fetches a single user.
   *
   * @param userId the user identifier
   * @return {@code 200 OK} with the user; {@code 404} via the global advice if the id is unknown
   */
  @GetMapping("/{userId}")
  @ResponseStatus(HttpStatus.OK)
  public UserResponse getById(@PathVariable Long userId) {
    return userService.getById(userId);
  }

  /**
   * Partially updates a user's mutable fields ({@code fullName}, {@code role}). Matches the
   * response is {@code 200 OK} with no body.
   *
   * @param userId the user identifier
   * @param request the validated partial update
   */
  @PostMapping("/update/{userId}")
  @ResponseStatus(HttpStatus.OK)
  public void update(@PathVariable Long userId, @Valid @RequestBody UpdateUserRequest request) {
    userService.update(userId, request);
  }

  /**
   * Hard-deletes a user. Returns {@code 200 OK} with no body per the README contract.
   *
   * @param userId the user identifier
   */
  @DeleteMapping("/{userId}")
  @ResponseStatus(HttpStatus.OK)
  public void delete(@PathVariable Long userId) {
    userService.delete(userId);
  }
}
