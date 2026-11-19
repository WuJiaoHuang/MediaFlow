package com.mediaflow.service;

import com.mediaflow.common.BusinessException;
import com.mediaflow.entity.*;
import com.mediaflow.mapper.*;
import com.mediaflow.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {
  private final EditingTaskMapper tasks;
  private final MediaAssetMapper assets;

  public AuthorizationService(EditingTaskMapper tasks, MediaAssetMapper assets) {
    this.tasks = tasks;
    this.assets = assets;
  }

  public boolean admin(CurrentUser actor) {
    return AccessContext.of(actor).admin;
  }

  public boolean taskReadable(EditingTask t, CurrentUser actor) {
    var c = AccessContext.of(actor);
    return c.admin
        || (c.editor && c.userId.equals(t.getAssigneeId()))
        || (c.reviewer && c.userId.equals(t.getReviewerId()));
  }

  public EditingTask task(Long id, CurrentUser actor, boolean lock) {
    var t = lock ? tasks.lockById(id) : tasks.selectById(id);
    if (t == null || !taskReadable(t, actor))
      throw new BusinessException(HttpStatus.NOT_FOUND, "任务不存在或无权访问");
    return t;
  }

  public MediaAsset asset(Long id, CurrentUser actor, Long taskId) {
    if (taskId != null) {
      task(taskId, actor, false);
      if (tasks.linked(taskId, id) == 0)
        throw new BusinessException(HttpStatus.NOT_FOUND, "任务素材不存在或无权访问");
    }
    var a = assets.selectById(id);
    if (a == null) throw new BusinessException(HttpStatus.NOT_FOUND, "素材不存在或无权访问");
    if (taskId != null) {
      if (!admin(actor) && !"ACTIVE".equals(a.getStatus()))
        throw new BusinessException(HttpStatus.NOT_FOUND, "任务素材不存在或无权访问");
    } else if (assets.readableCount(id, AccessContext.of(actor)) == 0) {
      if ("ARCHIVED".equals(a.getStatus()) && "LIBRARY".equals(a.getAccessScope()))
        throw new BusinessException(HttpStatus.FORBIDDEN, "归档素材仅管理员可访问");
      throw new BusinessException(HttpStatus.NOT_FOUND, "素材不存在或无权访问");
    }
    return a;
  }

  public void execute(EditingTask t, CurrentUser actor) {
    if (!admin(actor)
        && !(AccessContext.of(actor).editor && actor.user().id().equals(t.getAssigneeId())))
      throw new BusinessException(HttpStatus.FORBIDDEN, "仅当前执行人或管理员可执行此操作");
  }

  public void review(EditingTask t, CurrentUser actor) {
    if (!admin(actor)
        && !(AccessContext.of(actor).reviewer && actor.user().id().equals(t.getReviewerId())))
      throw new BusinessException(HttpStatus.FORBIDDEN, "仅当前审核人或管理员可审核");
  }

  public void requireAdmin(CurrentUser actor) {
    if (!admin(actor)) throw new BusinessException(HttpStatus.FORBIDDEN, "仅管理员可管理任务与素材范围");
  }
}
