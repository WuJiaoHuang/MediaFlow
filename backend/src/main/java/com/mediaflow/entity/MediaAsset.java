package com.mediaflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("media_asset")
public class MediaAsset {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String title;
  private String description;
  private Long fileId;

  @TableField(updateStrategy = FieldStrategy.ALWAYS)
  private Long categoryId;

  private String mediaType;
  private Long createdBy;
  private Long updatedBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private String status;
  private Integer version;
  private String uploadKey;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }

  public Long getFileId() {
    return fileId;
  }

  public void setFileId(Long v) {
    fileId = v;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Long v) {
    categoryId = v;
  }

  public String getMediaType() {
    return mediaType;
  }

  public void setMediaType(String v) {
    mediaType = v;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long v) {
    createdBy = v;
  }

  public Long getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(Long v) {
    updatedBy = v;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    status = v;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer v) {
    version = v;
  }

  public String getUploadKey() {
    return uploadKey;
  }

  public void setUploadKey(String v) {
    uploadKey = v;
  }
}
