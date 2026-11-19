package com.mediaflow.vo;

import java.time.LocalDateTime;

public class TaskView {
  public Long id;
  public String title;
  public String description;
  public Long createdBy;
  public String creatorName;
  public Long assigneeId;
  public String assigneeName;
  public Long reviewerId;
  public String reviewerName;
  public String priority;
  public LocalDateTime dueAt;
  public String status;
  public LocalDateTime createdAt;
  public LocalDateTime updatedAt;
  public Integer version;
}
