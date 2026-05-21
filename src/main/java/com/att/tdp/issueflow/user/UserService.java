package com.att.tdp.issueflow.user;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.common.error.DuplicateResourceException;
import com.att.tdp.issueflow.common.error.NotFoundException;
import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.att.tdp.issueflow.user.dto.UpdateUserRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;

/**
 * Business logic for the user management surface. All HTTP-status mapping happens outside this
 * class — the service throws semantic exceptions ({@link NotFoundException}, {@link
 * DuplicateResourceException}) and the {@code @RestControllerAdvice} maps them to status codes.
 *
 * <p>The endpoint contract is fixed by the project's {@code README.md} Users APIs table. Pagination
 * is intentionally absent on {@link #list()} because the README returns a plain JSON array.
 *
 * <p>{@code passwordHash} is intentionally left null on every persisted entity in this phase. The
 * {@link PasswordEncoder} dependency is injected for forward compatibility with the Phase-2
 * authentication feature; it has no callsite in this class.
 */
@Service
public class UserService {

  private static final String FIELD_USERNAME = "username";
  private static final String FIELD_EMAIL = "email";
  private static final String RESOURCE = "User";

  private final UserRepository userRepository;
  private final UserMapper userMapper;

  @SuppressWarnings("unused")
  private final PasswordEncoder passwordEncoder;

  public UserService(
      UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Creates a new user. Performs case-insensitive uniqueness pre-checks on {@code username} and
   * {@code email}, then catches any race-condition unique-constraint violation at save time and
   * re-throws it as a {@link DuplicateResourceException}.
   *
   * @param request the validated request body
   * @return the persisted user as a response DTO
   * @throws DuplicateResourceException if a user with the same username or email exists
   */
  @Transactional
  public UserResponse create(CreateUserRequest request) {
    if (userRepository.existsByUsernameIgnoreCase(request.username())) {
      throw new DuplicateResourceException(FIELD_USERNAME, request.username());
    }
    if (userRepository.existsByEmailIgnoreCase(request.email())) {
      throw new DuplicateResourceException(FIELD_EMAIL, request.email());
    }

    User user = userMapper.toEntity(request);
    try {
      User saved = userRepository.save(user);
      return userMapper.toResponse(saved);
    } catch (DataIntegrityViolationException e) {
      throw new DuplicateResourceException(FIELD_USERNAME, request.username());
    }
  }

  /**
   * Fetches a single user by id.
   *
   * @param id the user identifier
   * @return the user as a response DTO
   * @throws NotFoundException if no user has that id
   */
  @Transactional(readOnly = true)
  public UserResponse getById(Long id) {
    return userRepository
        .findById(id)
        .map(userMapper::toResponse)
        .orElseThrow(() -> new NotFoundException(RESOURCE, id));
  }

  /**
   * Returns every user. Results are ordered by id ascending for stable iteration; pagination is not
   * part of the README contract for this endpoint.
   *
   * @return all users
   */
  @Transactional(readOnly = true)
  public List<UserResponse> list() {
    return userRepository.findAll(Sort.by("id").ascending()).stream()
        .map(userMapper::toResponse)
        .toList();
  }

  /**
   * Applies a partial update to {@code fullName} and/or {@code role}. Username and email are
   * immutable and not present on the request DTO. An empty request is rejected upstream by the
   * DTO's {@code @AssertTrue} validation.
   *
   * @param id the user identifier
   * @param request the partial update
   * @throws NotFoundException if no user has that id
   */
  @Transactional
  public void update(Long id, UpdateUserRequest request) {
    User user = userRepository.findById(id).orElseThrow(() -> new NotFoundException(RESOURCE, id));
    if (request.fullName() != null) {
      user.setFullName(request.fullName());
    }
    if (request.role() != null) {
      user.setRole(request.role());
    }
    userRepository.save(user);
  }

  /**
   * Hard-deletes a user.
   *
   * @param id the user identifier
   * @throws NotFoundException if no user has that id
   */
  @Transactional
  public void delete(Long id) {
    if (!userRepository.existsById(id)) {
      throw new NotFoundException(RESOURCE, id);
    }
    userRepository.deleteById(id);
  }
}
