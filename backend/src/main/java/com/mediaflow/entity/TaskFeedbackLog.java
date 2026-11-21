package com.mediaflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("task_feedback_log")
public class TaskFeedbackLog {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long feedbackId;
  private Long operatorId;
  private String operatorName;
  private String operationType;
  private String oldStatus;
  private String newStatus;
  private String reason;
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

  public Long getOperatorId() {
    return operatorId;
  }

  public void setOperatorId(Long v) {
    operatorId = v;
  }

  public String getOperatorName() {
    return operatorName;
  }

  public void setOperatorName(String v) {
    operatorName = v;
  }

  public String getOperationType() {
    return operationType;
  }

  public void setOperationType(String v) {
    operationType = v;
  }

  public String getOldStatus() {
    return oldStatus;
  }

  public void setOldStatus(String v) {
    oldStatus = v;
  }

  public String getNewStatus() {
    return newStatus;
  }

  public void setNewStatus(String v) {
    newStatus = v;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String v) {
    reason = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }
}
