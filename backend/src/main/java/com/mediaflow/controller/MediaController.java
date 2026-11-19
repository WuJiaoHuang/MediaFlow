package com.mediaflow.controller;

import com.mediaflow.common.ApiResponse;
import com.mediaflow.dto.*;
import com.mediaflow.entity.MediaChangeLog;
import com.mediaflow.security.*;
import com.mediaflow.service.MediaService;
import com.mediaflow.storage.FileStorage;
import com.mediaflow.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
public class MediaController {
  private final MediaService service;
  private final FileStorage storage;
  private final JwtService jwt;

  public MediaController(MediaService service, FileStorage storage, JwtService jwt) {
    this.service = service;
    this.storage = storage;
    this.jwt = jwt;
  }

  @GetMapping("/assets")
  public ApiResponse<PageView<AssetView>> list(
      @Valid @ModelAttribute MediaQuery query, @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.list(query, actor));
  }

  @GetMapping("/assets/{id}")
  public ApiResponse<AssetView> detail(
      @PathVariable Long id, @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.detail(id, actor));
  }

  @GetMapping("/stats")
  public ApiResponse<MediaStats> stats(
      @Valid @ModelAttribute MediaQuery query, @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.stats(query, actor));
  }

  @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
  public ApiResponse<AssetView> upload(
      @RequestPart MultipartFile file,
      @RequestParam @NotBlank @Size(max = 200) String title,
      @RequestParam(defaultValue = "") @Size(max = 2000) String description,
      @RequestParam(required = false) @Positive Long categoryId,
      @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String uploadKey,
      @AuthenticationPrincipal CurrentUser actor)
      throws IOException {
    return ApiResponse.ok(service.upload(actor, file, title, description, categoryId, uploadKey));
  }

  @PutMapping("/assets/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
  public ApiResponse<AssetView> update(
      @PathVariable Long id,
      @Valid @RequestBody AssetUpdateRequest req,
      @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.update(id, actor, req));
  }

  @PostMapping("/assets/{id}/archive")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<AssetView> archive(
      @PathVariable Long id, @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.archive(id, actor));
  }

  @GetMapping("/changes")
  public ApiResponse<PageView<MediaChangeLog>> history(
      @Valid @ModelAttribute AuditQuery query, @AuthenticationPrincipal CurrentUser actor) {
    return ApiResponse.ok(service.history(query, actor));
  }

  public record MediaAccess(String contentUrl, String downloadUrl, int expiresIn) {}

  @PostMapping("/assets/{id}/access")
  public ResponseEntity<ApiResponse<MediaAccess>> access(
      @PathVariable Long id,
      @AuthenticationPrincipal CurrentUser actor,
      jakarta.servlet.http.HttpServletRequest req) {
    service.readable(id, actor);
    String path = "/api/media/assets/" + id;
    var cookie =
        ResponseCookie.from("mf_preview_" + id, jwt.issuePreview(actor.user().id(), id))
            .httpOnly(true)
            .secure(req.isSecure())
            .sameSite("Strict")
            .path(path)
            .maxAge(120)
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(ApiResponse.ok(new MediaAccess(path + "/content", path + "/download", 120)));
  }

  @GetMapping({"/assets/{id}/content", "/assets/{id}/download"})
  public ResponseEntity<Resource> content(
      @PathVariable Long id,
      @AuthenticationPrincipal CurrentUser actor,
      jakarta.servlet.http.HttpServletRequest req)
      throws IOException {
    var f = service.physical(id, actor);
    var resource = storage.resource(f.getStorageKey());
    boolean download = req.getRequestURI().endsWith("/download");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(f.getMimeType()))
        .contentLength(resource.contentLength())
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(f.getOriginalFilename(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(resource);
  }
}
