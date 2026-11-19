package com.mediaflow.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("editing_task_asset")
public class EditingTaskAsset {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long taskId;
  private Long assetId;

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

  public Long getAssetId() {
    return assetId;
  }

  public void setAssetId(Long value) {
    assetId = value;
  }
}
