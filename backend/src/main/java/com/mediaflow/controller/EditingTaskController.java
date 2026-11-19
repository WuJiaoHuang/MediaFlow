package com.mediaflow.controller;

import com.mediaflow.common.ApiResponse;
import com.mediaflow.dto.*;
import com.mediaflow.entity.EditingTaskLog;
import com.mediaflow.security.CurrentUser;
import com.mediaflow.service.EditingTaskService;
import com.mediaflow.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class EditingTaskController {
  private final EditingTaskService service;

  public EditingTaskController(EditingTaskService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<PageView<TaskView>> list(
      @Valid @ModelAttribute TaskQuery q, @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.list(q, actor));
  }

  @GetMapping("/people")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<List<TaskPerson>> people(@AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.people(actor));
  }

  @GetMapping("/{id}")
  public ApiResponse<TaskView> detail(
      @PathVariable Long id, @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.detail(id, actor));
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<TaskView> create(
      @Valid @RequestBody TaskRequest req, @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.create(req, actor));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<TaskView> update(
      @PathVariable Long id,
      @Valid @RequestBody TaskRequest req,
      @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.update(id, req, actor));
  }

  @GetMapping("/{id}/assets")
  public ApiResponse<List<AssetView>> assets(
      @PathVariable Long id, @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.taskAssets(id, actor));
  }

  @GetMapping("/{id}/logs")
  public ApiResponse<PageView<EditingTaskLog>> logs(
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") @Min(1) long page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
      @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.history(id, page, size, actor));
  }

  @PostMapping("/{id}/{action}")
  public ApiResponse<TaskView> action(
      @PathVariable Long id,
      @PathVariable String action,
      @Valid @RequestBody TaskActionRequest req,
      @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.action(id, action, req, actor));
  }
}
