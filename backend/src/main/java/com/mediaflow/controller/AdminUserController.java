package com.mediaflow.controller;

import com.mediaflow.common.ApiResponse;
import com.mediaflow.dto.*;
import com.mediaflow.security.CurrentUser;
import com.mediaflow.service.UserService;
import com.mediaflow.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
  private final UserService users;

  public AdminUserController(UserService users) {
    this.users = users;
  }

  @GetMapping("/users")
  public ApiResponse<PageView<UserView>> list(
      @RequestParam(defaultValue = "1") @Min(1) long page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
      @RequestParam(required = false) @Size(max = 100) String keyword) {
    return ApiResponse.ok(users.list(page, size, keyword));
  }

  @GetMapping("/users/{id}")
  public ApiResponse<UserView> get(@PathVariable Long id) {
    return ApiResponse.ok(users.view(id));
  }

  @PostMapping("/users")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public ApiResponse<UserView> create(@Valid @RequestBody CreateUserRequest req) {
    return ApiResponse.ok(users.create(req));
  }

  @PutMapping("/users/{id}")
  public ApiResponse<UserView> update(
      @AuthenticationPrincipal CurrentUser actor,
      @PathVariable Long id,
      @Valid @RequestBody UpdateUserRequest req) {
    return ApiResponse.ok(users.update(actor.user().id(), id, req));
  }

  @PutMapping("/users/{id}/roles")
  public ApiResponse<UserView> assign(
      @AuthenticationPrincipal CurrentUser actor,
      @PathVariable Long id,
      @Valid @RequestBody AssignRolesRequest req) {
    return ApiResponse.ok(users.assignRoles(actor.user().id(), id, req.roles()));
  }

  @GetMapping("/roles")
  public ApiResponse<List<RoleView>> roles() {
    return ApiResponse.ok(users.roles());
  }
}
