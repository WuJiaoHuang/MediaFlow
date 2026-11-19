package com.mediaflow.controller;

import com.mediaflow.common.ApiResponse;
import com.mediaflow.dto.CategoryRequest;
import com.mediaflow.entity.MediaCategory;
import com.mediaflow.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media/categories")
public class MediaCategoryController {
  private final CategoryService service;

  public MediaCategoryController(CategoryService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<MediaCategory>> list() {
    return ApiResponse.ok(service.list());
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<MediaCategory> create(@Valid @RequestBody CategoryRequest req) {
    return ApiResponse.ok(service.save(null, req));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<MediaCategory> update(
      @PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
    return ApiResponse.ok(service.save(id, req));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ApiResponse.ok(null);
  }
}
