package com.mediaflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("media_file")
public class MediaFile {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String originalFilename;
  private String storageKey;
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

  public String getOriginalFilename() {
    return originalFilename;
  }

  public void setOriginalFilename(String v) {
    originalFilename = v;
  }

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
