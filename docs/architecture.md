# 架构

## 范围与边界

采用前后端分离单体：Vue SPA → 同源 `/api` → Spring MVC → Service → Mapper → MySQL。
Stage 1 只有账号、角色与认证业务；不存在独立文件库，也没有后续媒体 / 任务接口。

后端包职责：controller 负责 HTTP 与 DTO 校验；service 承担业务规则与事务；mapper 使用 MyBatis-Plus / 参数化 SQL；entity 映射表；vo 显式限定响应字段；common 统一响应与异常；security 处理 JWT 与当前身份；config 配置框架；storage 预留统一文件存储接口。

JWT 过滤器先验签、校验时间和 issuer，再查询数据库当前用户及角色，构造 Spring Security Authentication。请求级管理员规则与方法级 `@PreAuthorize` 共同保护管理接口。过滤器只加入 SecurityFilterChain，关闭容器重复注册。

用户创建及角色整体替换使用事务；状态和角色编辑锁定目标用户行，避免针对同一用户的并发角色替换产生混合集合。数据库唯一约束处理重复用户名竞争；外键保护关系。管理员不能通过本次请求禁用自身或移除自身 ADMIN。

分页 size 为 1–100。Stage 1 列表按用户查询角色，存在每页 N+1 查询，规模增大时可改为分页 ID 后批量查询角色；当前最大每页 100，未引入缓存。

## 请求与错误

统一响应 `code/message/data`，成功码 0；40000 / 40100 / 40300 / 40400 / 40900 / 50000 分别对应 HTTP 400 / 401 / 403 / 404 / 409 / 500。没有把错误包装成 HTTP 200。框架层 HTTP 异常保留原状态和必要协议头：不支持方法为 405（含 Allow），不能满足响应格式为 406，不支持请求内容类型为 415；业务码对应 40500 / 40600 / 41500。内部错误不向客户端暴露 SQL、凭据或堆栈；日志记录异常类型用于排查。

Spring Security 无 Session，禁用 Cookie 表单登录和 Basic 登录的使用路径；仅接受显式 Bearer Token。开发通过 Vite 代理保持同源，没有通配 CORS。认证数据库故障返回 500，不误判为登录失败。

## 前端

Pinia 维护当前用户与 Token，Axios 添加 Authorization，401 清理凭据并导航登录页；403 显示权限提示。每次进入受保护路由重新请求 `/auth/me`，再检查管理员角色。菜单展示仅为交互体验；后端承担真正的授权。

工作台展示当前身份、账号状态和真实可用入口，没有编造业务统计或批量空白页面。管理员页面支持分页搜索、创建、编辑、角色分配。个人信息页展示自身资料。登录失败、表格加载和保存期间均有反馈。

## 文件边界与未来数据权限

`FileStorage` 仅定义 store/open/delete 边界及不透明对象 key；当前没有实现或对外接口。后续实现需要大小 / MIME / 内容校验、防路径穿越、存储命名隔离与受控下载。

**角色权限不等于数据权限。** Stage 2 及任务阶段必须在 Service/Mapper 查询条件中加入拥有者、任务分配者或审核授权约束；读取详情、下载、修改和统计均须应用相同规则。不能仅靠前端筛选或“有 EDITOR 角色”放行全部任务。

## 版本依据

兼容性核对来源：[Spring Boot 3.5 / Java 17](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[MyBatis-Plus 分页拆分说明](https://baomidou.com/plugins/pagination/)、[springdoc / Boot 对照表](https://springdoc.org/)。最终可用性由本项目构建、JUnit、真实 MySQL 与浏览器测试共同确认。
