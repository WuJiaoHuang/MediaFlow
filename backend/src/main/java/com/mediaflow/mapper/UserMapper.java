package com.mediaflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mediaflow.entity.SysUser;
import org.apache.ibatis.annotations.*;

public interface UserMapper extends BaseMapper<SysUser> {
  @Select("SELECT * FROM sys_user WHERE id=#{id} FOR UPDATE")
  SysUser lockById(Long id);
}
