package com.mediaflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("task_feedback")
public class TaskFeedback {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long taskId;
  private Long assetId;
  private String assetTitle;
  private String mediaType;
  private Long creatorId;
  private String creatorName;
  private String type;
  private String content;
  private Long timecodeMs;
  private String status;
  private String createKey;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private Integer version;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public Long getTaskId() {
    return taskId;
  }

  public void setTaskId(Long v) {
    taskId = v;
  }

  public Long getAssetId() {
    return assetId;
  }

  public void setAssetId(Long v) {
    assetId = v;
  }

  public String getAssetTitle() {
    return assetTitle;
  }

  public void setAssetTitle(String v) {
    assetTitle = v;
  }

  public String getMediaType() {
    return mediaType;
  }

  public void setMediaType(String v) {
    mediaType = v;
  }

  public Long getCreatorId() {
    return creatorId;
  }

  public void setCreatorId(Long v) {
    creatorId = v;
  }

  public String getCreatorName() {
    return creatorName;
  }

  public void setCreatorName(String v) {
    creatorName = v;
  }

  public String getType() {
    return type;
  }

  public void setType(String v) {
    type = v;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String v) {
    content = v;
  }

  public Long getTimecodeMs() {
    return timecodeMs;
  }

  public void setTimecodeMs(Long v) {
    timecodeMs = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    status = v;
  }

  public String getCreateKey() {
    return createKey;
  }

  public void setCreateKey(String v) {
    createKey = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime v) {
    updatedAt = v;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer v) {
    version = v;
  }
}
