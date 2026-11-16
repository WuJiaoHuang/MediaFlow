package com.mediaflow.mapper;

import com.mediaflow.vo.RoleView;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface RoleMapper {
  @Select(
      "SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON r.id=ur.role_id WHERE"
          + " ur.user_id=#{userId} ORDER BY r.id")
  List<String> codes(Long userId);

  @Select("SELECT id,role_code,role_name,description FROM sys_role ORDER BY id")
  List<RoleView> all();

  @Select("SELECT id FROM sys_role WHERE role_code=#{code}")
  Long findId(String code);

  @Delete("DELETE FROM sys_user_role WHERE user_id=#{userId}")
  void clear(Long userId);

  @Insert("INSERT INTO sys_user_role(user_id,role_id) VALUES(#{userId},#{roleId})")
  void assign(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
