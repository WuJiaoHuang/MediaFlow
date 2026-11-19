package com.mediaflow.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public class AuditQuery {
  @Min(1)
  public long page = 1;

  @Min(1)
  @Max(100)
  public long size = 20;

  @Positive public Long assetId = null;
  @Positive public Long operatorId = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  public LocalDateTime from = null;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  public LocalDateTime to = null;

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

  public Long getAssetId() {
    return assetId;
  }

  public void setAssetId(Long v) {
    assetId = v;
  }

  public Long getOperatorId() {
    return operatorId;
  }

  public void setOperatorId(Long v) {
    operatorId = v;
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
}
