package com.mediaflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("editing_task_log")
public class EditingTaskLog {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long taskId;
  private Long operatorId;
  private String operatorName;
  private String operationType;
  private String fieldName;
  private String oldValue;
  private String newValue;
  private String reason;
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }

  public Long getTaskId() {
    return taskId;
  }

  public void setTaskId(Long value) {
    taskId = value;
  }

  public Long getOperatorId() {
    return operatorId;
  }

  public void setOperatorId(Long value) {
    operatorId = value;
  }

  public String getOperatorName() {
    return operatorName;
  }

  public void setOperatorName(String value) {
    operatorName = value;
  }

  public String getOperationType() {
    return operationType;
  }

  public void setOperationType(String value) {
    operationType = value;
  }

  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String value) {
    fieldName = value;
  }

  public String getOldValue() {
    return oldValue;
  }

  public void setOldValue(String value) {
    oldValue = value;
  }

  public String getNewValue() {
    return newValue;
  }

  public void setNewValue(String value) {
    newValue = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    reason = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }
}
