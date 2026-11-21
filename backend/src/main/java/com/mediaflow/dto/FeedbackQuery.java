package com.mediaflow.dto;

import jakarta.validation.constraints.*;

public class FeedbackQuery {
  @Min(1)
  private long page = 1;

  @Min(1)
  @Max(100)
  private long size = 20;

  @Positive private Long assetId;

  @Pattern(regexp = "TEXT|AUDIO|VIDEO")
  private String type;

  @Pattern(regexp = "OPEN|RESOLVED|CLOSED|REOPENED")
  private String status;

  @Pattern(regexp = "ASC|DESC")
  private String direction = "DESC";

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

  public String getType() {
    return type;
  }

  public void setType(String v) {
    type = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    status = v;
  }

  public String getDirection() {
    return direction;
  }

  public void setDirection(String v) {
    direction = v;
  }
}
