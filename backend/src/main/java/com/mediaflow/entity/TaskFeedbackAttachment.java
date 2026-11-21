package com.mediaflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("task_feedback_attachment")
public class TaskFeedbackAttachment {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long feedbackId;
  private String originalFilename;
  @com.fasterxml.jackson.annotation.JsonIgnore private String storageKey;
  private String mimeType;
  private Long fileSize;
  private Long uploadedBy;
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public Long getFeedbackId() {
    return feedbackId;
  }

  public void setFeedbackId(Long v) {
    feedbackId = v;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public void setOriginalFilename(String v) {
    originalFilename = v;
  }

  @com.fasterxml.jackson.annotation.JsonIgnore
  public String getStorageKey() {
    return storageKey;
  }

  public void setStorageKey(String v) {
    storageKey = v;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String v) {
    mimeType = v;
  }

  public Long getFileSize() {
    return fileSize;
  }

  public void setFileSize(Long v) {
    fileSize = v;
  }

  public Long getUploadedBy() {
    return uploadedBy;
  }

  public void setUploadedBy(Long v) {
    uploadedBy = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }
}
