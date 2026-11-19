package com.mediaflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("editing_task")
public class EditingTask {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String title;
  private String description;
  private Long createdBy;
  private Long assigneeId;
  private Long reviewerId;
  private String priority;
  private LocalDateTime dueAt;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private Integer version;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    title = value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    description = value;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long value) {
    createdBy = value;
  }

  public Long getAssigneeId() {
    return assigneeId;
  }

  public void setAssigneeId(Long value) {
    assigneeId = value;
  }

  public Long getReviewerId() {
    return reviewerId;
  }

  public void setReviewerId(Long value) {
    reviewerId = value;
  }

  public String getPriority() {
    return priority;
  }

  public void setPriority(String value) {
    priority = value;
  }

  public LocalDateTime getDueAt() {
    return dueAt;
  }

  public void setDueAt(LocalDateTime value) {
    dueAt = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime value) {
    updatedAt = value;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer value) {
    version = value;
  }
}
