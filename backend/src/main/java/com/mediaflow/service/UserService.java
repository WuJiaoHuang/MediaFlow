package com.mediaflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mediaflow.common.BusinessException;
import com.mediaflow.dto.*;
import com.mediaflow.entity.SysUser;
import com.mediaflow.mapper.*;
import com.mediaflow.vo.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserMapper users;
  private final RoleMapper roles;
  private final PasswordEncoder passwords;

  public UserService(UserMapper users, RoleMapper roles, PasswordEncoder passwords) {
    this.users = users;
    this.roles = roles;
    this.passwords = passwords;
  }

  public UserView view(Long id) {
    return UserView.of(require(id), roles.codes(id));
  }

  public SysUser require(Long id) {
    var u = users.selectById(id);
    if (u == null) throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
    return u;
  }

  public UserView login(String name, String password) {
    var u = users.selectOne(new QueryWrapper<SysUser>().eq("username", name));
    // A dummy BCrypt check reduces username enumeration through timing differences.
    String hash =
        u == null
            ? "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW"
            : u.getPasswordHash();
    boolean valid = passwords.matches(password, hash);
    if (u == null || !valid || u.getStatus() != 1)
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误，或账号已禁用");
    return UserView.of(u, roles.codes(u.getId()));
  }

  public PageView<UserView> list(long page, long size, String keyword) {
    var q = new QueryWrapper<SysUser>();
    if (keyword != null && !keyword.isBlank())
      q.and(w -> w.like("username", keyword).or().like("display_name", keyword));
    q.orderByDesc("id");
    var p = users.selectPage(new Page<>(page, size), q);
    Map<Long, List<String>> grouped = new HashMap<>();
    if (!p.getRecords().isEmpty())
      for (var row : roles.batchCodes(p.getRecords().stream().map(SysUser::getId).toList()))
        grouped.computeIfAbsent(row.userId, k -> new ArrayList<>()).add(row.roleCode);
    return new PageView<>(
        p.getRecords().stream()
            .map(u -> UserView.of(u, grouped.getOrDefault(u.getId(), List.of())))
            .toList(),
        p.getTotal(),
        page,
        size);
  }

  public List<RoleView> roles() {
    return roles.all();
  }

  private Map<String, Long> validateRoles(Set<String> codes) {
    var result = new LinkedHashMap<String, Long>();
    for (var code : codes) {
      Long id = roles.findId(code);
      if (id == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "未知角色: " + code);
      result.put(code, id);
    }
    return result;
  }

  @Transactional
  public UserView create(CreateUserRequest req) {
    var ids = validateRoles(req.roles());
    var u = new SysUser();
    u.setUsername(req.username());
    u.setDisplayName(req.displayName());
    u.setPasswordHash(passwords.encode(req.password()));
    u.setStatus(1);
    u.setCreatedAt(LocalDateTime.now());
    u.setUpdatedAt(u.getCreatedAt());
    users.insert(u);
    ids.values().forEach(id -> roles.assign(u.getId(), id));
    return view(u.getId());
  }

  @Transactional
  public UserView update(Long actor, Long id, UpdateUserRequest req) {
    var u = users.lockById(id);
    if (u == null) throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
    if (actor.equals(id) && req.status() == 0)
      throw new BusinessException(HttpStatus.CONFLICT, "不能禁用当前管理员自身");
    u.setDisplayName(req.displayName());
    u.setStatus(req.status());
    u.setUpdatedAt(LocalDateTime.now());
    users.updateById(u);
    return view(id);
  }

  @Transactional
  public UserView assignRoles(Long actor, Long id, Set<String> codes) {
    if (users.lockById(id) == null) throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
    var ids = validateRoles(codes);
    if (actor.equals(id) && !codes.contains("ADMIN"))
      throw new BusinessException(HttpStatus.CONFLICT, "不能移除当前管理员自身的 ADMIN 角色");
    roles.clear(id);
    ids.values().forEach(roleId -> roles.assign(id, roleId));
    return view(id);
  }
}
