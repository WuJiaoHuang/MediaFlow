package com.mediaflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mediaflow.entity.MediaCategory;
import java.util.List;
import org.apache.ibatis.annotations.Select;

public interface MediaCategoryMapper extends BaseMapper<MediaCategory> {
  @Select("SELECT * FROM media_category ORDER BY id FOR UPDATE")
  List<MediaCategory> lockAll();

  @Select("SELECT * FROM media_category WHERE id=#{id} FOR UPDATE")
  MediaCategory lockById(Long id);
}
