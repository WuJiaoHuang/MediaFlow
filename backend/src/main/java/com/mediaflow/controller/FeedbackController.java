package com.mediaflow.controller;

import com.mediaflow.common.ApiResponse;
import com.mediaflow.dto.*;
import com.mediaflow.entity.*;
import com.mediaflow.security.*;
import com.mediaflow.service.FeedbackService;
import com.mediaflow.storage.FileStorage;
import com.mediaflow.vo.PageView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tasks/{taskId}/feedback")
public class FeedbackController {
  private final FeedbackService service;
  private final FileStorage storage;
  private final JwtService jwt;

  public FeedbackController(FeedbackService service, FileStorage storage, JwtService jwt) {
    this.service = service;
    this.storage = storage;
    this.jwt = jwt;
  }

  @GetMapping
  public ApiResponse<PageView<TaskFeedback>> list(
      @PathVariable Long taskId,
      @Valid @ModelAttribute FeedbackQuery q,
      @AuthenticationPrincipal CurrentUser a) {
    return ApiResponse.ok(service.list(taskId, q, a));
  }

  @GetMapping("/summary")
  public ApiResponse<Map<String, Long>> summary(
      @PathVariable Long taskId, @AuthenticationPrincipal CurrentUser a) {
    return ApiResponse.ok(service.summary(taskId, a));
  }

  @GetMapping("/{id}")
  public ApiResponse<FeedbackService.Detail> detail(
      @PathVariable Long taskId, @PathVariable Long id, @AuthenticationPrincipal CurrentUser a) {
    return ApiResponse.ok(service.detail(taskId, id, a));
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<FeedbackService.Detail> create(
      @PathVariable Long taskId,
      @Valid @RequestBody FeedbackRequest req,
      @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String key,
      @AuthenticationPrincipal CurrentUser a)
      throws IOException {
    return ApiResponse.ok(service.create(taskId, req, key, null, a));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<FeedbackService.Detail> upload(
      @PathVariable Long taskId,
      @RequestParam @Pattern(regexp = "AUDIO|VIDEO") String type,
      @RequestParam(defaultValue = "") @Size(max = 4000) String content,
      @RequestParam(required = false) @Positive Long assetId,
      @RequestParam(required = false) @PositiveOrZero Long timecodeMs,
      @RequestPart MultipartFile file,
      @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String key,
      @AuthenticationPrincipal CurrentUser a)
      throws IOException {
    return ApiResponse.ok(
        service.create(
            taskId, new FeedbackRequest(type, content, assetId, timecodeMs), key, file, a));
  }

  @GetMapping("/{id}/replies")
  public ApiResponse<PageView<TaskFeedbackReply>> replies(
      @PathVariable Long taskId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") @Min(1) long page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
      @AuthenticationPrincipal CurrentUser a) {
    return ApiResponse.ok(service.replies(taskId, id, page, size, a));
  }

  @GetMapping("/{id}/logs")
  public ApiResponse<PageView<TaskFeedbackLog>> logs(
      @PathVariable Long taskId,
      @PathVariable Long id,
      @RequestParam(defaultValue = "1") @Min(1) long page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
      @AuthenticationPrincipal CurrentUser a) {
    return ApiResponse.ok(service.history(taskId, id, page, size, a));
  }

  @PostMapping("/{id}/replies")
  public ApiResponse<FeedbackService.Detail> reply(
      @PathVariable Long taskId,
      @PathVariable Long id,
      @Valid @RequestBody FeedbackReplyRequest req,
      @AuthenticationPrincipal CurrentUser a) {
    return ApiResponse.ok(service.reply(taskId, id, req, a));
  }

  @PostMapping("/{id}/{action:resolve|close|reopen}")
  public ApiResponse<FeedbackService.Detail> action(
      @PathVariable Long taskId,
      @PathVariable Long id,
      @PathVariable String action,
      @Valid @RequestBody TaskActionRequest req,
      @AuthenticationPrincipal CurrentUser a) {
    return ApiResponse.ok(service.action(taskId, id, action, req, a));
  }

  public record Access(String contentUrl, String downloadUrl, int expiresIn) {}

  @PostMapping("/{id}/attachments/{attachmentId}/access")
  public ResponseEntity<ApiResponse<Access>> access(
      @PathVariable Long taskId,
      @PathVariable Long id,
      @PathVariable Long attachmentId,
      @AuthenticationPrincipal CurrentUser a,
      jakarta.servlet.http.HttpServletRequest req) {
    service.attachment(taskId, id, attachmentId, a);
    String path = "/api/tasks/" + taskId + "/feedback/" + id + "/attachments/" + attachmentId;
    var cookie =
        ResponseCookie.from(
                "mf_feedback_" + attachmentId,
                jwt.issueFeedback(a.user().id(), taskId, id, attachmentId))
            .httpOnly(true)
            .secure(req.isSecure())
            .sameSite("Strict")
            .path(path)
            .maxAge(120)
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(ApiResponse.ok(new Access(path + "/content", path + "/download", 120)));
  }

  @GetMapping({
    "/{id}/attachments/{attachmentId}/content",
    "/{id}/attachments/{attachmentId}/download"
  })
  public ResponseEntity<Resource> content(
      @PathVariable Long taskId,
      @PathVariable Long id,
      @PathVariable Long attachmentId,
      @AuthenticationPrincipal CurrentUser a,
      jakarta.servlet.http.HttpServletRequest req)
      throws IOException {
    var f = service.attachment(taskId, id, attachmentId, a);
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
