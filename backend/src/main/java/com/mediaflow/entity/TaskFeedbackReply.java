package com.mediaflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("task_feedback_reply")
public class TaskFeedbackReply {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long feedbackId;
  private Long creatorId;
  private String creatorName;
  private String content;
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

  public String getContent() {
    return content;
  }

  public void setContent(String v) {
    content = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }
}
