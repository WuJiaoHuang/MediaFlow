package com.mediaflow.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public class MediaQuery {
  @Min(1)
  private long page = 1;

  @Min(1)
  @Max(100)
  private long size = 12;

  @Size(max = 200)
  private String keyword = null;

  @Min(0)
  private Long categoryId = null;

  @Pattern(regexp = "IMAGE|AUDIO|VIDEO")
  private String mediaType = null;

  @Positive private Long uploadedBy = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private LocalDateTime from = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private LocalDateTime to = null;

  @NotNull
  @Pattern(regexp = "ACTIVE|ARCHIVED|ALL")
  private String status = "ACTIVE";

  @NotNull
  @Pattern(regexp = "createdAt|title|fileSize")
  private String sort = "createdAt";

  @NotNull
  @Pattern(regexp = "asc|desc")
  private String direction = "desc";

  public long getPage() {
    return page;
  }

  public void setPage(long v) {
    page = v;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long v) {
    size = v;
  }

  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String v) {
    keyword = v;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Long v) {
    categoryId = v;
  }

  public String getMediaType() {
    return mediaType;
  }

  public void setMediaType(String v) {
    mediaType = v;
  }

  public Long getUploadedBy() {
    return uploadedBy;
  }

  public void setUploadedBy(Long v) {
    uploadedBy = v;
  }

  public LocalDateTime getFrom() {
    return from;
  }

  public void setFrom(LocalDateTime v) {
    from = v;
  }

  public LocalDateTime getTo() {
    return to;
  }

  public void setTo(LocalDateTime v) {
    to = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    status = v;
  }

  public String getSort() {
    return sort;
  }

  public void setSort(String v) {
    sort = v;
  }

  public String getDirection() {
    return direction;
  }

  public void setDirection(String v) {
    direction = v;
  }
}
