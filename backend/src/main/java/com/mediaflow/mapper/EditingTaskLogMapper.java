package com.mediaflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mediaflow.entity.EditingTaskLog;
import org.apache.ibatis.annotations.*;

public interface EditingTaskLogMapper extends BaseMapper<EditingTaskLog> {
  @Select("SELECT * FROM editing_task_log WHERE task_id=#{id} ORDER BY created_at DESC,id DESC")
  IPage<EditingTaskLog> history(Page<EditingTaskLog> page, @Param("id") Long id);
}
