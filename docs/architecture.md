# 架构

## 范围与边界

采用前后端分离单体：Vue SPA → 同源 `/api` → Spring MVC → Service → Mapper → MySQL。
Stage 1 提供账号与认证；Stage 2 提供媒体业务与内部存储实现。不存在独立文件库和剪辑任务模块。

后端包职责：controller 负责 HTTP 与 DTO 校验；service 承担业务规则与事务；mapper 使用 MyBatis-Plus / 参数化 SQL；entity 映射表；vo 显式限定响应字段；common 统一响应与异常；security 处理 JWT 与当前身份；config 配置框架；storage 预留统一文件存储接口。

JWT 过滤器先验签、校验时间和 issuer，再查询数据库当前用户及角色，构造 Spring Security Authentication。请求级管理员规则与方法级 `@PreAuthorize` 共同保护管理接口。过滤器只加入 SecurityFilterChain，关闭容器重复注册。

用户创建及角色整体替换使用事务；状态和角色编辑锁定目标用户行，避免针对同一用户的并发角色替换产生混合集合。数据库唯一约束处理重复用户名竞争；外键保护关系。管理员不能通过本次请求禁用自身或移除自身 ADMIN。

分页 size 为 1–100。用户分页先查询当前页，再一次性批量查询该页 user_id 的角色，按 ID 分组组装原 UserView；空页不发角色批量 SQL，保持原响应兼容。每页最大 100，未引入缓存。

## 请求与错误

统一响应 `code/message/data`，成功码 0；40000 / 40100 / 40300 / 40400 / 40900 / 50000 分别对应 HTTP 400 / 401 / 403 / 404 / 409 / 500。没有把错误包装成 HTTP 200。框架层 HTTP 异常保留原状态和必要协议头：不支持方法为 405（含 Allow），不能满足响应格式为 406，不支持请求内容类型为 415；业务码对应 40500 / 40600 / 41500。内部错误不向客户端暴露 SQL、凭据或堆栈；日志记录异常类型用于排查。

Spring Security 无 Session，禁用 Cookie 表单登录和 Basic 登录的使用路径；仅接受显式 Bearer Token。开发通过 Vite 代理保持同源，没有通配 CORS。认证数据库故障返回 500，不误判为登录失败。

## 前端

Pinia 维护当前用户与 Token，Axios 添加 Authorization，401 清理凭据并导航登录页；403 显示权限提示。每次进入受保护路由重新请求 `/auth/me`，再检查管理员角色。菜单展示仅为交互体验；后端承担真正的授权。

工作台展示当前身份、账号状态和真实可用入口，没有编造业务统计或批量空白页面。管理员页面支持分页搜索、创建、编辑、角色分配。个人信息页展示自身资料。登录失败、表格加载和保存期间均有反馈。

## 文件边界与未来数据权限

`FileStorage` 定义 store/open/resource/delete 内部边界，LocalFileStorageService 提供本地实现，不直接暴露 key 读取接口。Stage 2 已落实大小、内容 / MIME、UUID、防路径穿越和授权下载。文件与审计业务的事务关系见 media-management.md / media-audit.md。

**角色权限不等于数据权限。** Stage 2 及任务阶段必须在 Service/Mapper 查询条件中加入拥有者、任务分配者或审核授权约束；读取详情、下载、修改和统计均须应用相同规则。不能仅靠前端筛选或“有 EDITOR 角色”放行全部任务。

## 版本依据

兼容性核对来源：[Spring Boot 3.5 / Java 17](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[MyBatis-Plus 分页拆分说明](https://baomidou.com/plugins/pagination/)、[springdoc / Boot 对照表](https://springdoc.org/)。最终可用性由本项目构建、JUnit、真实 MySQL 与浏览器测试共同确认。


## Stage 2 的业务链路

MediaController → MediaService → MediaAssetMapper / MediaFileMapper / MediaChangeLogMapper。CategoryController → CategoryService。上传校验、写盘、元数据 / 素材 / 创建审计在一条明确流程中处理；回滚回调清理新文件。编辑与归档持有素材行锁并在同事务显式追加真实字段日志。version 防止过期页面覆盖修改。

列表 / 统计共用 XML filters SQL；列表由 MyBatis-Plus 数据库分页。统计在只读事务执行，避免来自不同时间快照的聚合混用。分类为两级树，状态不影响历史素材可见性；分类结构编辑序列化后验证。文件属于业务资产，归档不删除物理对象。

浏览器使用短期、单素材的只读预览 Cookie 发起原生媒体请求；Bearer 访问令牌不写 URL，专用预览凭据不能进入其他 API。每个请求均执行实时用户查询和素材权限判断，FileSystemResource 提供 Range 流式响应。细节见 media-management.md。

前端移除 Element Plus 全量安装，使用 unplugin-vue-components + ElementPlusResolver 自动按需导入组件和 CSS；程序调用的 Message / MessageBox / Loading 样式显式保留，根组件使用中文 ElConfigProvider。页面继续路由懒加载。

Stage 2 暂时允许所有已登录角色读取 ACTIVE 素材；EDITOR 写入另加所有权条件，REVIEWER 没有写入权限，ARCHIVED 仅 ADMIN 可读。此策略不是最终任务级隔离。
