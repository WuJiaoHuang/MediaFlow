package com.mediaflow.dto;

import jakarta.validation.constraints.*;

public class TaskQuery {
  @Min(1)
  private long page = 1;

  @Min(1)
  @Max(100)
  private long size = 12;

  @Size(max = 200)
  private String keyword = null;

  @Pattern(regexp = "PENDING|PROCESSING|REVIEWING|COMPLETED")
  private String status = null;

  @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT")
  private String priority = null;

  @Positive private Long assigneeId = null;
  @Positive private Long reviewerId = null;
  private boolean mine = false;

  @NotNull
  @Pattern(regexp = "createdAt|dueAt|priority")
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    status = v;
  }

  public String getPriority() {
    return priority;
  }

  public void setPriority(String v) {
    priority = v;
  }

  public Long getAssigneeId() {
    return assigneeId;
  }

  public void setAssigneeId(Long v) {
    assigneeId = v;
  }

  public Long getReviewerId() {
    return reviewerId;
  }

  public void setReviewerId(Long v) {
    reviewerId = v;
  }

  public boolean getMine() {
    return mine;
  }

  public void setMine(boolean v) {
    mine = v;
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
