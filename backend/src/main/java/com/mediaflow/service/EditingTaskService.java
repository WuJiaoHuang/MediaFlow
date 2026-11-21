package com.mediaflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediaflow.common.BusinessException;
import com.mediaflow.dto.*;
import com.mediaflow.entity.*;
import com.mediaflow.mapper.*;
import com.mediaflow.security.*;
import com.mediaflow.vo.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditingTaskService {
  private final EditingTaskMapper tasks;
  private final EditingTaskLogMapper logs;
  private final UserMapper users;
  private final RoleMapper roles;
  private final MediaAssetMapper assets;
  private final AuthorizationService auth;
  private final ObjectMapper json;
  private final TaskFeedbackMapper feedback;

  public EditingTaskService(
      EditingTaskMapper tasks,
      EditingTaskLogMapper logs,
      UserMapper users,
      RoleMapper roles,
      MediaAssetMapper assets,
      AuthorizationService auth,
      ObjectMapper json,
      TaskFeedbackMapper feedback) {
    this.feedback = feedback;
    this.tasks = tasks;
    this.logs = logs;
    this.users = users;
    this.roles = roles;
    this.assets = assets;
    this.auth = auth;
    this.json = json;
  }

  private BusinessException invalid(String m) {
    return new BusinessException(HttpStatus.BAD_REQUEST, m);
  }

  private BusinessException conflict(String m) {
    return new BusinessException(HttpStatus.CONFLICT, m);
  }

  public PageView<TaskView> list(TaskQuery q, CurrentUser actor) {
    var p = tasks.search(new Page<>(q.getPage(), q.getSize()), q, AccessContext.of(actor));
    return new PageView<>(p.getRecords(), p.getTotal(), q.getPage(), q.getSize());
  }

  public TaskView detail(Long id, CurrentUser actor) {
    auth.task(id, actor, false);
    return tasks.detail(id);
  }

  public List<AssetView> taskAssets(Long id, CurrentUser actor) {
    auth.task(id, actor, false);
    return assets.taskAssets(id, AccessContext.of(actor));
  }

  public PageView<EditingTaskLog> history(Long id, long page, long size, CurrentUser actor) {
    auth.task(id, actor, false);
    var p = logs.history(new Page<>(page, size), id);
    return new PageView<>(p.getRecords(), p.getTotal(), page, size);
  }

  public List<TaskPerson> people(CurrentUser actor) {
    auth.requireAdmin(actor);
    return tasks.people();
  }

  private void person(Long id, boolean editor) {
    var user = users.selectById(id);
    if (user == null || user.getStatus() != 1) throw invalid("执行人或审核人不存在或已禁用");
    var codes = roles.codes(id);
    if (editor
        ? !codes.contains("EDITOR")
        : !codes.contains("REVIEWER") && !codes.contains("ADMIN"))
      throw invalid(editor ? "执行人必须具有 EDITOR 角色" : "审核人必须具有 REVIEWER 或 ADMIN 角色");
  }

  private List<Long> validate(TaskRequest req) {
    java.util.stream.Stream.of(req.assigneeId(), req.reviewerId())
        .distinct()
        .sorted()
        .forEach(users::lockById);
    person(req.assigneeId(), true);
    person(req.reviewerId(), false);
    if (new HashSet<>(req.assetIds()).size() != req.assetIds().size()) throw invalid("不能重复关联相同素材");
    var sorted = req.assetIds().stream().sorted().toList();
    for (Long id : sorted) {
      var asset = assets.lockById(id);
      if (asset == null || !"ACTIVE".equals(asset.getStatus())) throw invalid("关联素材不存在或已归档");
    }
    return sorted;
  }

  private String encoded(Object value) {
    if (value == null) return null;
    if (value instanceof String s) return s;
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot encode task audit", e);
    }
  }

  private void log(
      EditingTask task,
      CurrentUser actor,
      String operation,
      String field,
      Object oldValue,
      Object newValue,
      String reason) {
    var l = new EditingTaskLog();
    l.setTaskId(task.getId());
    l.setOperatorId(actor.user().id());
    l.setOperatorName(actor.user().displayName());
    l.setOperationType(operation);
    l.setFieldName(field);
    l.setOldValue(encoded(oldValue));
    l.setNewValue(encoded(newValue));
    l.setReason(reason);
    l.setCreatedAt(LocalDateTime.now());
    logs.insert(l);
  }

  private boolean change(
      EditingTask task,
      CurrentUser actor,
      String op,
      String field,
      Object oldValue,
      Object newValue) {
    if (Objects.equals(oldValue, newValue)) return false;
    log(task, actor, op, field, oldValue, newValue, null);
    return true;
  }

  private void write(EditingTask task, int expected) {
    task.setUpdatedAt(LocalDateTime.now());
    task.setVersion(expected + 1);
    if (tasks.updateVersioned(task, expected) != 1) throw conflict("任务已被其他人修改，请刷新后重试");
  }

  @Transactional
  public TaskView create(TaskRequest req, CurrentUser actor) {
    auth.requireAdmin(actor);
    var ids = validate(req);
    var t = new EditingTask();
    t.setTitle(req.title().trim());
    t.setDescription(req.description().trim());
    t.setCreatedBy(actor.user().id());
    t.setAssigneeId(req.assigneeId());
    t.setReviewerId(req.reviewerId());
    t.setPriority(req.priority());
    t.setDueAt(req.dueAt());
    t.setStatus("PENDING");
    t.setVersion(0);
    t.setCreatedAt(LocalDateTime.now());
    t.setUpdatedAt(t.getCreatedAt());
    tasks.insert(t);
    for (Long id : ids) tasks.link(t.getId(), id);
    log(
        t,
        actor,
        "CREATE",
        "task",
        null,
        Map.of(
            "title",
            t.getTitle(),
            "description",
            t.getDescription(),
            "assigneeId",
            t.getAssigneeId(),
            "reviewerId",
            t.getReviewerId(),
            "priority",
            t.getPriority(),
            "assetIds",
            ids),
        null);
    return tasks.detail(t.getId());
  }

  @Transactional
  public TaskView update(Long id, TaskRequest req, CurrentUser actor) {
    auth.requireAdmin(actor);
    var t = auth.task(id, actor, true);
    if (t.getStatus().equals("COMPLETED")) throw conflict("已完成任务不能修改");
    if (req.version() == null) throw invalid("修改任务必须提供 version");
    if (!t.getVersion().equals(req.version())) throw conflict("任务已被其他人修改，请刷新后重试");
    var ids = validate(req);
    boolean dirty = false;
    dirty |= change(t, actor, "TITLE_CHANGED", "title", t.getTitle(), req.title().trim());
    dirty |=
        change(
            t,
            actor,
            "REQUIREMENTS_CHANGED",
            "description",
            t.getDescription(),
            req.description().trim());
    dirty |=
        change(t, actor, "ASSIGNEE_CHANGED", "assignee_id", t.getAssigneeId(), req.assigneeId());
    dirty |=
        change(t, actor, "REVIEWER_CHANGED", "reviewer_id", t.getReviewerId(), req.reviewerId());
    dirty |= change(t, actor, "PRIORITY_CHANGED", "priority", t.getPriority(), req.priority());
    dirty |= change(t, actor, "DUE_DATE_CHANGED", "due_at", t.getDueAt(), req.dueAt());
    var oldIds = tasks.assetIds(id);
    boolean assetChange = change(t, actor, "ASSETS_CHANGED", "asset_ids", oldIds, ids);
    dirty |= assetChange;
    if (dirty) {
      t.setTitle(req.title().trim());
      t.setDescription(req.description().trim());
      t.setAssigneeId(req.assigneeId());
      t.setReviewerId(req.reviewerId());
      t.setPriority(req.priority());
      t.setDueAt(req.dueAt());
      write(t, req.version());
      if (assetChange) {
        for (Long assetId : oldIds)
          if (!ids.contains(assetId)) {
            if (feedback.assetReferences(id, assetId) > 0) throw conflict("已有反馈的素材关联不能移除");
            tasks.unlink(id, assetId);
          }
        for (Long assetId : ids) if (!oldIds.contains(assetId)) tasks.link(id, assetId);
      }
    }
    return tasks.detail(id);
  }

  @Transactional
  public TaskView action(Long id, String action, TaskActionRequest req, CurrentUser actor) {
    var t = auth.task(id, actor, true);
    String from, to, op;
    switch (action) {
      case "start" -> {
        auth.execute(t, actor);
        from = "PENDING";
        to = "PROCESSING";
        op = "START";
      }
      case "submit" -> {
        auth.execute(t, actor);
        from = "PROCESSING";
        to = "REVIEWING";
        op = "SUBMIT";
      }
      case "approve" -> {
        auth.review(t, actor);
        from = "REVIEWING";
        to = "COMPLETED";
        op = "APPROVE";
      }
      case "reject" -> {
        auth.review(t, actor);
        from = "REVIEWING";
        to = "PROCESSING";
        op = "REJECT";
        if (req.reason() == null || req.reason().isBlank()) throw invalid("审核退回必须填写原因");
      }
      default -> throw new BusinessException(HttpStatus.NOT_FOUND, "操作不存在");
    }
    if (!t.getVersion().equals(req.version())) throw conflict("任务版本冲突，请刷新后重试");
    if (!from.equals(t.getStatus())) throw conflict("当前状态不允许此操作");
    String reason = action.equals("reject") ? req.reason().trim() : null;
    if (action.equals("submit")) {
      long count = feedback.unresolved(id);
      if (count > 0) {
        if (!auth.admin(actor)) throw conflict("仍有 " + count + " 条未解决反馈，不能提交审核");
        if (req.reason() == null || req.reason().isBlank() || req.reason().trim().length() > 1800)
          throw invalid("管理员例外提交必须填写原因（1..1800 字）");
        op = "SUBMIT_OVERRIDE";
        reason = "未解决数量=" + count + "；" + req.reason().trim();
      }
    }
    if (action.equals("approve")) {
      long count = feedback.unconfirmed(id);
      if (count > 0) throw conflict("仍有 " + count + " 条反馈尚未确认解决，不能审核通过");
    }
    t.setStatus(to);
    write(t, req.version());
    log(t, actor, op, "status", from, to, reason);
    return tasks.detail(id);
  }
}
