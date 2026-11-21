package com.mediaflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mediaflow.dto.TaskQuery;
import com.mediaflow.entity.EditingTask;
import com.mediaflow.security.AccessContext;
import com.mediaflow.vo.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface EditingTaskMapper extends BaseMapper<EditingTask> {
  @Select("SELECT * FROM editing_task WHERE id=#{id} FOR UPDATE")
  EditingTask lockById(Long id);

  IPage<TaskView> search(
      Page<TaskView> page, @Param("q") TaskQuery query, @Param("auth") AccessContext auth);

  TaskView detail(Long id);

  @Select("SELECT asset_id FROM editing_task_asset WHERE task_id=#{id} ORDER BY asset_id")
  List<Long> assetIds(Long id);

  @Select("SELECT COUNT(*) FROM editing_task_asset WHERE task_id=#{taskId} AND asset_id=#{assetId}")
  long linked(@Param("taskId") Long taskId, @Param("assetId") Long assetId);

  @Insert("INSERT INTO editing_task_asset(task_id,asset_id) VALUES(#{taskId},#{assetId})")
  void link(@Param("taskId") Long taskId, @Param("assetId") Long assetId);

  @Delete("DELETE FROM editing_task_asset WHERE task_id=#{id}")
  void clearAssets(Long id);

  @Delete("DELETE FROM editing_task_asset WHERE task_id=#{taskId} AND asset_id=#{assetId}")
  void unlink(@Param("taskId") Long taskId, @Param("assetId") Long assetId);

  @Update(
      "UPDATE editing_task SET"
          + " title=#{t.title},description=#{t.description},assignee_id=#{t.assigneeId},reviewer_id=#{t.reviewerId},priority=#{t.priority},due_at=#{t.dueAt},status=#{t.status},updated_at=#{t.updatedAt},version=#{t.version}"
          + " WHERE id=#{t.id} AND version=#{expected}")
  int updateVersioned(@Param("t") EditingTask task, @Param("expected") int expected);

  @Select(
      "SELECT u.id,u.username,u.display_name,r.role_code FROM sys_user u JOIN sys_user_role ur ON"
          + " u.id=ur.user_id JOIN sys_role r ON r.id=ur.role_id WHERE u.status=1 AND r.role_code"
          + " IN ('ADMIN','EDITOR','REVIEWER') ORDER BY u.id,r.id")
  List<TaskPerson> people();
}
