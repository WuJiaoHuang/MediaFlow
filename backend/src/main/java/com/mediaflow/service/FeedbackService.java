package com.mediaflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mediaflow.common.BusinessException;
import com.mediaflow.dto.*;
import com.mediaflow.entity.*;
import com.mediaflow.mapper.*;
import com.mediaflow.security.*;
import com.mediaflow.storage.*;
import com.mediaflow.vo.PageView;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FeedbackService {
  private final AuthorizationService auth;
  private final EditingTaskMapper tasks;
  private final TaskFeedbackMapper feedback;
  private final TaskFeedbackReplyMapper replies;
  private final TaskFeedbackAttachmentMapper attachments;
  private final TaskFeedbackLogMapper logs;
  private final FileStorage storage;
  private final FeedbackMediaValidator validator;

  public FeedbackService(
      AuthorizationService auth,
      EditingTaskMapper tasks,
      TaskFeedbackMapper feedback,
      TaskFeedbackReplyMapper replies,
      TaskFeedbackAttachmentMapper attachments,
      TaskFeedbackLogMapper logs,
      FileStorage storage,
      FeedbackMediaValidator validator) {
    this.auth = auth;
    this.tasks = tasks;
    this.feedback = feedback;
    this.replies = replies;
    this.attachments = attachments;
    this.logs = logs;
    this.storage = storage;
    this.validator = validator;
  }

  private BusinessException error(HttpStatus status, String message) {
    return new BusinessException(status, message);
  }

  private void writable(EditingTask t) {
    if (!Set.of("PROCESSING", "REVIEWING").contains(t.getStatus()))
      throw error(HttpStatus.CONFLICT, "当前任务状态下反馈只读");
  }

  private boolean reviewer(EditingTask t, CurrentUser a) {
    return auth.admin(a)
        || (AccessContext.of(a).reviewer && a.user().id().equals(t.getReviewerId()));
  }

  private TaskFeedback owned(Long taskId, Long id, CurrentUser a, boolean lock) {
    auth.task(taskId, a, lock);
    var f = lock ? feedback.lockById(id) : feedback.selectById(id);
    if (f == null
        || !f.getTaskId().equals(taskId)
        || (f.getAssetId() != null && tasks.linked(taskId, f.getAssetId()) == 0))
      throw error(HttpStatus.NOT_FOUND, "反馈不存在或无权访问");
    return f;
  }

  private void version(TaskFeedback f, Integer v) {
    if (v == null || !v.equals(f.getVersion())) throw error(HttpStatus.CONFLICT, "反馈版本冲突，请刷新后重试");
  }

  private void write(TaskFeedback f) {
    int expected = f.getVersion();
    f.setUpdatedAt(LocalDateTime.now());
    if (feedback.write(f, expected) != 1) throw error(HttpStatus.CONFLICT, "反馈版本冲突，请刷新后重试");
    f.setVersion(expected + 1);
  }

  private void log(TaskFeedback f, CurrentUser a, String op, String old, String reason) {
    var l = new TaskFeedbackLog();
    l.setFeedbackId(f.getId());
    l.setOperatorId(a.user().id());
    l.setOperatorName(a.user().displayName());
    l.setOperationType(op);
    l.setOldStatus(old);
    l.setNewStatus(f.getStatus());
    l.setReason(reason);
    l.setCreatedAt(LocalDateTime.now());
    logs.insert(l);
  }

  public record Detail(TaskFeedback feedback, TaskFeedbackAttachment attachment) {}

  public Detail detail(Long taskId, Long id, CurrentUser a) {
    var f = owned(taskId, id, a, false);
    return new Detail(
        f, attachments.selectOne(new QueryWrapper<TaskFeedbackAttachment>().eq("feedback_id", id)));
  }

  public PageView<TaskFeedback> list(Long taskId, FeedbackQuery q, CurrentUser a) {
    auth.task(taskId, a, false);
    if (q.getAssetId() != null && tasks.linked(taskId, q.getAssetId()) == 0)
      throw error(HttpStatus.NOT_FOUND, "任务素材不存在或无权访问");
    var w =
        new QueryWrapper<TaskFeedback>()
            .eq("task_id", taskId)
            .eq(q.getAssetId() != null, "asset_id", q.getAssetId())
            .eq(q.getType() != null, "type", q.getType())
            .eq(q.getStatus() != null, "status", q.getStatus())
            .orderBy(true, "ASC".equals(q.getDirection()), "created_at", "id");
    var p = feedback.selectPage(new Page<>(q.getPage(), q.getSize()), w);
    return new PageView<>(p.getRecords(), p.getTotal(), q.getPage(), q.getSize());
  }

  public Map<String, Long> summary(Long taskId, CurrentUser a) {
    auth.task(taskId, a, false);
    long total = feedback.selectCount(new QueryWrapper<TaskFeedback>().eq("task_id", taskId));
    long open = feedback.unresolved(taskId);
    long pending = feedback.unconfirmed(taskId);
    return Map.of(
        "total", total, "unresolved", open, "resolved", pending - open, "closed", total - pending);
  }

  @Transactional(rollbackFor = Exception.class)
  public Detail create(
      Long taskId, FeedbackRequest req, String key, MultipartFile file, CurrentUser a)
      throws IOException {
    var t = auth.task(taskId, a, true);
    // Retry is read-only, but never bypasses current task authorization.
    var existing =
        feedback.selectOne(
            new QueryWrapper<TaskFeedback>()
                .eq("task_id", taskId)
                .eq("creator_id", a.user().id())
                .eq("create_key", key));
    if (existing != null) return detail(taskId, existing.getId(), a);
    writable(t);
    if (t.getStatus().equals("REVIEWING") && !reviewer(t, a))
      throw error(HttpStatus.FORBIDDEN, "待审核时仅当前审核人或管理员可提出反馈");
    String content = req.content() == null ? "" : req.content().trim();
    if (req.type().equals("TEXT") && (content.isBlank() || file != null))
      throw error(HttpStatus.BAD_REQUEST, "文字反馈必须填写内容且不能附带文件");
    if (!req.type().equals("TEXT") && file == null)
      throw error(HttpStatus.BAD_REQUEST, "语音或视频反馈必须上传附件");
    MediaAsset asset = null;
    if (req.assetId() != null) {
      asset = auth.asset(req.assetId(), a, taskId);
      if (!"ACTIVE".equals(asset.getStatus())) throw error(HttpStatus.BAD_REQUEST, "不能对已归档素材新增反馈");
    }
    if (req.timecodeMs() != null
        && (asset == null || !Set.of("AUDIO", "VIDEO").contains(asset.getMediaType())))
      throw error(HttpStatus.BAD_REQUEST, "时间码仅适用于任务关联的音视频素材");
    String mime = file == null ? null : validator.detect(file, req.type());
    var f = new TaskFeedback();
    f.setTaskId(taskId);
    f.setAssetId(req.assetId());
    f.setAssetTitle(asset == null ? null : asset.getTitle());
    f.setMediaType(asset == null ? null : asset.getMediaType());
    f.setCreatorId(a.user().id());
    f.setCreatorName(a.user().displayName());
    f.setType(req.type());
    f.setContent(content);
    f.setTimecodeMs(req.timecodeMs());
    f.setStatus("OPEN");
    f.setCreateKey(key);
    f.setVersion(0);
    f.setCreatedAt(LocalDateTime.now());
    f.setUpdatedAt(f.getCreatedAt());
    feedback.insert(f);
    if (file != null) {
      FileStorage.StoredObject stored;
      try (var in = file.getInputStream()) {
        stored = storage.store(in, file.getSize(), mime);
      }
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status != STATUS_COMMITTED)
                try {
                  storage.delete(stored.key());
                } catch (IOException e) {
                  org.slf4j.LoggerFactory.getLogger(FeedbackService.class)
                      .error("Feedback rollback file cleanup failed");
                }
            }
          });
      var att = new TaskFeedbackAttachment();
      att.setFeedbackId(f.getId());
      att.setOriginalFilename(file.getOriginalFilename());
      att.setStorageKey(stored.key());
      att.setMimeType(mime);
      att.setFileSize(stored.size());
      att.setUploadedBy(a.user().id());
      att.setCreatedAt(f.getCreatedAt());
      attachments.insert(att);
    }
    log(f, a, "CREATE", null, null);
    return detail(taskId, f.getId(), a);
  }

  public PageView<TaskFeedbackReply> replies(
      Long taskId, Long id, long page, long size, CurrentUser a) {
    owned(taskId, id, a, false);
    var p =
        replies.selectPage(
            new Page<>(page, size),
            new QueryWrapper<TaskFeedbackReply>()
                .eq("feedback_id", id)
                .orderByAsc("created_at", "id"));
    return new PageView<>(p.getRecords(), p.getTotal(), page, size);
  }

  public PageView<TaskFeedbackLog> history(
      Long taskId, Long id, long page, long size, CurrentUser a) {
    owned(taskId, id, a, false);
    var p =
        logs.selectPage(
            new Page<>(page, size),
            new QueryWrapper<TaskFeedbackLog>()
                .eq("feedback_id", id)
                .orderByAsc("created_at", "id"));
    return new PageView<>(p.getRecords(), p.getTotal(), page, size);
  }

  @Transactional
  public Detail reply(Long taskId, Long id, FeedbackReplyRequest req, CurrentUser a) {
    var t = auth.task(taskId, a, true);
    writable(t);
    var f = owned(taskId, id, a, true);
    version(f, req.version());
    if (f.getStatus().equals("CLOSED")) throw error(HttpStatus.CONFLICT, "请先重新打开已确认意见再回复");
    var r = new TaskFeedbackReply();
    r.setFeedbackId(id);
    r.setCreatorId(a.user().id());
    r.setCreatorName(a.user().displayName());
    r.setContent(req.content().trim());
    r.setCreatedAt(LocalDateTime.now());
    replies.insert(r);
    write(f);
    log(f, a, "REPLY", f.getStatus(), "replyId=" + r.getId());
    return detail(taskId, id, a);
  }

  @Transactional
  public Detail action(Long taskId, Long id, String action, TaskActionRequest req, CurrentUser a) {
    var t = auth.task(taskId, a, true);
    writable(t);
    var f = owned(taskId, id, a, true);
    version(f, req.version());
    String old = f.getStatus(), next, reason = null;
    switch (action) {
      case "resolve" -> {
        auth.execute(t, a);
        if (!Set.of("OPEN", "REOPENED").contains(old))
          throw error(HttpStatus.CONFLICT, "仅待处理或重新打开的反馈可以标记已处理");
        next = "RESOLVED";
      }
      case "close", "reopen" -> {
        if (!reviewer(t, a) && !a.user().id().equals(f.getCreatorId()))
          throw error(HttpStatus.FORBIDDEN, "仅当前审核人、仍有任务授权的提出人或管理员可以确认或重开");
        if (action.equals("close")) {
          if (!old.equals("RESOLVED")) throw error(HttpStatus.CONFLICT, "仅已处理反馈可以确认解决");
          next = "CLOSED";
        } else {
          if (!Set.of("RESOLVED", "CLOSED").contains(old))
            throw error(HttpStatus.CONFLICT, "仅已处理或已确认反馈可以重开");
          if (req.reason() == null || req.reason().isBlank())
            throw error(HttpStatus.BAD_REQUEST, "重新打开必须填写原因");
          reason = req.reason().trim();
          next = "REOPENED";
        }
      }
      default -> throw error(HttpStatus.NOT_FOUND, "反馈操作不存在");
    }
    f.setStatus(next);
    write(f);
    log(f, a, action.toUpperCase(Locale.ROOT), old, reason);
    return detail(taskId, id, a);
  }

  public TaskFeedbackAttachment attachment(Long taskId, Long id, Long attachmentId, CurrentUser a) {
    owned(taskId, id, a, false);
    var att = attachments.selectById(attachmentId);
    if (att == null || !att.getFeedbackId().equals(id))
      throw error(HttpStatus.NOT_FOUND, "附件不存在或无权访问");
    return att;
  }
}
