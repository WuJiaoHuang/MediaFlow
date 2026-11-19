package com.mediaflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mediaflow.dto.MediaQuery;
import com.mediaflow.entity.MediaAsset;
import com.mediaflow.vo.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface MediaAssetMapper extends BaseMapper<MediaAsset> {
  @Select("SELECT * FROM media_asset WHERE id=#{id} FOR UPDATE")
  MediaAsset lockById(Long id);

  IPage<AssetView> search(
      Page<AssetView> page,
      @Param("q") MediaQuery query,
      @Param("auth") com.mediaflow.security.AccessContext auth);

  AssetView detail(Long id);

  long readableCount(
      @Param("id") Long id, @Param("auth") com.mediaflow.security.AccessContext auth);

  List<AssetView> taskAssets(
      @Param("id") Long id, @Param("auth") com.mediaflow.security.AccessContext auth);

  MediaStats stats(
      @Param("q") MediaQuery query,
      @Param("recentSince") LocalDateTime recentSince,
      @Param("auth") com.mediaflow.security.AccessContext auth);

  List<MediaStats.CategoryCount> categoryCounts(
      @Param("q") MediaQuery query, @Param("auth") com.mediaflow.security.AccessContext auth);
}
