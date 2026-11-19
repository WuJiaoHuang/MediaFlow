package com.mediaflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mediaflow.common.BusinessException;
import com.mediaflow.dto.CategoryRequest;
import com.mediaflow.entity.*;
import com.mediaflow.mapper.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
  private final MediaCategoryMapper categories;
  private final MediaAssetMapper assets;

  public CategoryService(MediaCategoryMapper categories, MediaAssetMapper assets) {
    this.categories = categories;
    this.assets = assets;
  }

  public List<MediaCategory> list() {
    return categories.selectList(new QueryWrapper<MediaCategory>().orderByAsc("sort_order", "id"));
  }

  public MediaCategory require(Long id) {
    var c = categories.selectById(id);
    if (c == null) throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
    return c;
  }

  public String label(Long id) {
    return id == null ? "未分类" : require(id).getCategoryName();
  }

  public void requireAssignable(Long id) {
    if (id == null) return;
    var c = categories.lockById(id);
    if (c == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "分类不存在");
    if (c.getStatus() != 1
        || (c.getParentId() != null && require(c.getParentId()).getStatus() != 1))
      throw new BusinessException(HttpStatus.CONFLICT, "分类或上级已停用，不能分配新素材");
  }

  @Transactional
  public MediaCategory save(Long id, CategoryRequest req) {
    categories.lockAll();
    MediaCategory c = id == null ? new MediaCategory() : require(id);
    if (req.parentId() != null) {
      if (req.parentId().equals(id)) throw new BusinessException(HttpStatus.CONFLICT, "分类不能引用自身");
      var parent = require(req.parentId());
      if (parent.getParentId() != null)
        throw new BusinessException(HttpStatus.CONFLICT, "最多支持两级分类");
      if (id != null
          && categories.selectCount(new QueryWrapper<MediaCategory>().eq("parent_id", id)) > 0)
        throw new BusinessException(HttpStatus.CONFLICT, "有子分类的分类不能移到其他分类下");
    }
    c.setCategoryName(req.categoryName().trim());
    c.setParentId(req.parentId());
    c.setSortOrder(req.sortOrder());
    c.setStatus(req.status());
    c.setUpdatedAt(LocalDateTime.now());
    if (id == null) {
      c.setCreatedAt(c.getUpdatedAt());
      categories.insert(c);
    } else categories.updateById(c);
    return c;
  }

  @Transactional
  public void delete(Long id) {
    categories.lockAll();
    require(id);
    if (categories.selectCount(new QueryWrapper<MediaCategory>().eq("parent_id", id)) > 0
        || assets.selectCount(new QueryWrapper<MediaAsset>().eq("category_id", id)) > 0)
      throw new BusinessException(HttpStatus.CONFLICT, "分类仍有关联素材（含归档）或子分类，不能删除");
    categories.deleteById(id);
  }
}
