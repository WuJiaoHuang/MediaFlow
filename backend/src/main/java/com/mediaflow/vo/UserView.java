package com.mediaflow.vo;

import com.mediaflow.entity.SysUser;
import java.time.LocalDateTime;
import java.util.List;

public record UserView(
    Long id,
    String username,
    String displayName,
    Integer status,
    List<String> roles,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
  public static UserView of(SysUser u, List<String> roles) {
    return new UserView(
        u.getId(),
        u.getUsername(),
        u.getDisplayName(),
        u.getStatus(),
        roles,
        u.getCreatedAt(),
        u.getUpdatedAt());
  }
}
