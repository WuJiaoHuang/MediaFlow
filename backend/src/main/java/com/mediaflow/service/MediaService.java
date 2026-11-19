package com.mediaflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mediaflow.common.BusinessException;
import com.mediaflow.dto.*;
import com.mediaflow.entity.*;
import com.mediaflow.mapper.*;
import com.mediaflow.security.CurrentUser;
import com.mediaflow.storage.*;
import com.mediaflow.vo.*;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
  private final MediaAssetMapper assets;
  private final MediaFileMapper files;
  private final MediaChangeLogMapper logs;
  private final CategoryService categories;
  private final FileStorage storage;
  private final MediaTypeValidator validator;

  public MediaService(
      MediaAssetMapper assets,
      MediaFileMapper files,
      MediaChangeLogMapper logs,
      CategoryService categories,
      FileStorage storage,
      MediaTypeValidator validator) {
    this.assets = assets;
    this.files = files;
    this.logs = logs;
    this.categories = categories;
    this.storage = storage;
    this.validator = validator;
  }

  public boolean admin(CurrentUser actor) {
    return actor.user().roles().contains("ADMIN");
  }

  private void writable(CurrentUser actor, MediaAsset a) {
    if (!admin(actor)
        && (!actor.user().roles().contains("EDITOR")
            || !a.getCreatedBy().equals(actor.user().id())))
      throw new BusinessException(HttpStatus.FORBIDDEN, "只能编辑本人上传的素材");
    if (!a.getStatus().equals("ACTIVE"))
      throw new BusinessException(HttpStatus.CONFLICT, "归档素材不能编辑");
  }

  public MediaAsset readable(Long id, CurrentUser actor) {
    var a = assets.selectById(id);
    if (a == null) throw new BusinessException(HttpStatus.NOT_FOUND, "素材不存在");
    if (!admin(actor) && !a.getStatus().equals("ACTIVE"))
      throw new BusinessException(HttpStatus.FORBIDDEN, "归档素材仅管理员可访问");
    return a;
  }

  public AssetView detail(Long id, CurrentUser actor) {
    readable(id, actor);
    return assets.detail(id);
  }

  private void queryPolicy(MediaQuery q, CurrentUser actor) {
    if (!admin(actor) && !q.getStatus().equals("ACTIVE"))
      throw new BusinessException(HttpStatus.FORBIDDEN, "无权查询归档素材");
    range(q.getFrom(), q.getTo());
  }

  private void range(LocalDateTime from, LocalDateTime to) {
    if (from != null && to != null && from.isAfter(to))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "开始时间不能晚于结束时间");
  }

  public PageView<AssetView> list(MediaQuery q, CurrentUser actor) {
    queryPolicy(q, actor);
    var p = assets.search(new Page<>(q.getPage(), q.getSize()), q);
    return new PageView<>(p.getRecords(), p.getTotal(), q.getPage(), q.getSize());
  }

  @Transactional(readOnly = true)
  public MediaStats stats(MediaQuery q, CurrentUser actor) {
    queryPolicy(q, actor);
    var result = assets.stats(q, LocalDateTime.now().minusDays(7));
    var counts = assets.categoryCounts(q);
    var byId = new HashMap<Long, MediaStats.CategoryCount>();
    MediaStats.CategoryCount uncategorized = null;
    for (var c : counts) {
      if (c.categoryId == null) uncategorized = c;
      else byId.put(c.categoryId, c);
    }
    var all = new ArrayList<MediaStats.CategoryCount>();
    for (var c : categories.list()) {
      var count = byId.get(c.getId());
      if (count == null) {
        count = new MediaStats.CategoryCount();
        count.categoryId = c.getId();
        count.categoryName = c.getCategoryName();
      }
      all.add(count);
    }
    if (uncategorized == null) {
      uncategorized = new MediaStats.CategoryCount();
      uncategorized.categoryName = "未分类";
    }
    all.add(uncategorized);
    result.categories = all;
    return result;
  }

  @Transactional(rollbackFor = Exception.class)
  public AssetView upload(
      CurrentUser actor,
      MultipartFile file,
      String title,
      String description,
      Long categoryId,
      String uploadKey)
      throws IOException {
    if (!admin(actor) && !actor.user().roles().contains("EDITOR"))
      throw new BusinessException(HttpStatus.FORBIDDEN, "无权上传素材");
    if (file.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择非空媒体文件");
    categories.requireAssignable(categoryId);
    if (assets.selectCount(
            new QueryWrapper<MediaAsset>()
                .eq("created_by", actor.user().id())
                .eq("upload_key", uploadKey))
        > 0) throw new BusinessException(HttpStatus.CONFLICT, "该上传请求已完成，请勿重复提交");
    var detected = validator.detect(file);
    FileStorage.StoredObject stored;
    try (var in = file.getInputStream()) {
      stored = storage.store(in, file.getSize(), detected.mime());
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED)
              try {
                storage.delete(stored.key());
              } catch (IOException e) {
                org.slf4j.LoggerFactory.getLogger(MediaService.class)
                    .error("Upload rollback cleanup failed for storage key {}", stored.key());
              }
          }
        });
    var now = LocalDateTime.now();
    var physical = new MediaFile();
    physical.setOriginalFilename(file.getOriginalFilename());
    physical.setStorageKey(stored.key());
    physical.setMimeType(stored.contentType());
    physical.setFileSize(stored.size());
    physical.setUploadedBy(actor.user().id());
    physical.setCreatedAt(now);
    files.insert(physical);
    var a = new MediaAsset();
    a.setTitle(title.trim());
    a.setDescription(description);
    a.setFileId(physical.getId());
    a.setCategoryId(categoryId);
    a.setMediaType(detected.mediaType());
    a.setCreatedBy(actor.user().id());
    a.setUpdatedBy(actor.user().id());
    a.setCreatedAt(now);
    a.setUpdatedAt(now);
    a.setStatus("ACTIVE");
    a.setVersion(0);
    a.setUploadKey(uploadKey);
    assets.insert(a);
    change(a, actor, "CREATE", "title", null, a.getTitle(), null, null);
    change(a, actor, "CREATE", "description", null, description, null, null);
    change(
        a,
        actor,
        "CREATE",
        "category_id",
        null,
        str(categoryId),
        null,
        categories.label(categoryId));
    change(
        a,
        actor,
        "CREATE",
        "file_id",
        null,
        str(physical.getId()),
        null,
        file.getOriginalFilename());
    change(a, actor, "CREATE", "status", null, "ACTIVE", null, null);
    return assets.detail(a.getId());
  }

  private String str(Object value) {
    return value == null ? null : value.toString();
  }

  public void change(
      MediaAsset a,
      CurrentUser actor,
      String operation,
      String field,
      String oldValue,
      String newValue,
      String oldLabel,
      String newLabel) {
    var log = new MediaChangeLog();
    log.setAssetId(a.getId());
    log.setOperatorId(actor.user().id());
    log.setOperatorName(actor.user().displayName());
    log.setAssetTitle(a.getTitle());
    log.setOperationType(operation);
    log.setFieldName(field);
    log.setOldValue(oldValue);
    log.setNewValue(newValue);
    log.setOldLabel(oldLabel);
    log.setNewLabel(newLabel);
    log.setCreatedAt(LocalDateTime.now());
    logs.insert(log);
  }

  @Transactional
  public AssetView update(Long id, CurrentUser actor, AssetUpdateRequest req) {
    var a = assets.lockById(id);
    if (a == null) throw new BusinessException(HttpStatus.NOT_FOUND, "素材不存在");
    writable(actor, a);
    if (!a.getVersion().equals(req.version()))
      throw new BusinessException(HttpStatus.CONFLICT, "素材已被其他人修改，请刷新后重试");
    boolean changed = false;
    String title = req.title().trim();
    if (!a.getTitle().equals(title)) {
      change(a, actor, "UPDATE", "title", a.getTitle(), title, null, null);
      a.setTitle(title);
      changed = true;
    }
    if (!a.getDescription().equals(req.description())) {
      change(a, actor, "UPDATE", "description", a.getDescription(), req.description(), null, null);
      a.setDescription(req.description());
      changed = true;
    }
    if (!Objects.equals(a.getCategoryId(), req.categoryId())) {
      categories.requireAssignable(req.categoryId());
      change(
          a,
          actor,
          "UPDATE",
          "category_id",
          str(a.getCategoryId()),
          str(req.categoryId()),
          categories.label(a.getCategoryId()),
          categories.label(req.categoryId()));
      a.setCategoryId(req.categoryId());
      changed = true;
    }
    if (changed) {
      a.setUpdatedBy(actor.user().id());
      a.setUpdatedAt(LocalDateTime.now());
      a.setVersion(a.getVersion() + 1);
      assets.updateById(a);
    }
    return assets.detail(id);
  }

  @Transactional
  public AssetView archive(Long id, CurrentUser actor) {
    if (!admin(actor)) throw new BusinessException(HttpStatus.FORBIDDEN, "仅管理员可归档素材");
    var a = assets.lockById(id);
    if (a == null) throw new BusinessException(HttpStatus.NOT_FOUND, "素材不存在");
    if (!a.getStatus().equals("ARCHIVED")) {
      change(a, actor, "ARCHIVE", "status", a.getStatus(), "ARCHIVED", null, null);
      a.setStatus("ARCHIVED");
      a.setVersion(a.getVersion() + 1);
      a.setUpdatedBy(actor.user().id());
      a.setUpdatedAt(LocalDateTime.now());
      assets.updateById(a);
    }
    return assets.detail(id);
  }

  public PageView<MediaChangeLog> history(AuditQuery q, CurrentUser actor) {
    range(q.from, q.to);
    if (q.assetId != null) readable(q.assetId, actor);
    var p = logs.search(new Page<>(q.page, q.size), q, admin(actor));
    return new PageView<>(p.getRecords(), p.getTotal(), q.page, q.size);
  }

  public MediaFile physical(Long id, CurrentUser actor) {
    return files.selectById(readable(id, actor).getFileId());
  }
}
