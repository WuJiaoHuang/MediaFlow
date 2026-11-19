package com.mediaflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("media_category")
public class MediaCategory {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String categoryName;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long parentId;

  private Integer sortOrder;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public String getCategoryName() {
    return categoryName;
  }

  public void setCategoryName(String v) {
    categoryName = v;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long v) {
    parentId = v;
  }

  public Integer getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(Integer v) {
    sortOrder = v;
  }

  public Integer getStatus() {
    return status;
  }

  public void setStatus(Integer v) {
    status = v;
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
}
