package com.mediaflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("media_change_log")
public class MediaChangeLog {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long assetId;
  private Long operatorId;
  private String operatorName;
  private String assetTitle;
  private String operationType;
  private String fieldName;
  private String oldValue;
  private String newValue;
  private String oldLabel;
  private String newLabel;
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
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

  public String getOperatorName() {
    return operatorName;
  }

  public void setOperatorName(String v) {
    operatorName = v;
  }

  public String getAssetTitle() {
    return assetTitle;
  }

  public void setAssetTitle(String v) {
    assetTitle = v;
  }

  public String getOperationType() {
    return operationType;
  }

  public void setOperationType(String v) {
    operationType = v;
  }

  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String v) {
    fieldName = v;
  }

  public String getOldValue() {
    return oldValue;
  }

  public void setOldValue(String v) {
    oldValue = v;
  }

  public String getNewValue() {
    return newValue;
  }

  public void setNewValue(String v) {
    newValue = v;
  }

  public String getOldLabel() {
    return oldLabel;
  }

  public void setOldLabel(String v) {
    oldLabel = v;
  }

  public String getNewLabel() {
    return newLabel;
  }

  public void setNewLabel(String v) {
    newLabel = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }
}
