package com.mediaflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mediaflow.entity.TaskFeedback;
import org.apache.ibatis.annotations.*;

public interface TaskFeedbackMapper extends BaseMapper<TaskFeedback> {
  @Select("SELECT * FROM task_feedback WHERE id=#{id} FOR UPDATE")
  TaskFeedback lockById(Long id);

  @Select(
      "SELECT COUNT(*) FROM task_feedback WHERE task_id=#{taskId} AND status IN"
          + " ('OPEN','REOPENED')")
  long unresolved(Long taskId);

  @Select("SELECT COUNT(*) FROM task_feedback WHERE task_id=#{taskId} AND status<>'CLOSED'")
  long unconfirmed(Long taskId);

  @Select("SELECT COUNT(*) FROM task_feedback WHERE task_id=#{taskId} AND asset_id=#{assetId}")
  long assetReferences(@Param("taskId") Long taskId, @Param("assetId") Long assetId);

  @Update(
      "UPDATE task_feedback SET status=#{f.status},updated_at=#{f.updatedAt},version=version+1"
          + " WHERE id=#{f.id} AND version=#{expected}")
  int write(@Param("f") TaskFeedback f, @Param("expected") int expected);
}
