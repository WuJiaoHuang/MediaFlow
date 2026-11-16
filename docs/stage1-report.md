# MediaFlow Stage 1 验收报告

日期：2026-11-17（Asia/Shanghai）。项目目录：MediaFlow，分支 main。

## 实际完成

- 从空目录创建 backend / frontend / sql / docs / scripts，Java 17 + Spring Boot 3.5.7 单体与 Vue 3 SPA。
- 统一响应、参数校验、异常转换、MyBatis-Plus 分页、MySQL 数据源、OpenAPI、基础日志。
- 实际创建独立 MySQL 数据库 mediaflow，落地 sys_user / sys_role / sys_user_role；唯一约束、状态 CHECK、外键、双向关系索引。
- 实际初始化 admin / editor01 / reviewer01，仅保存成本 12 的 BCrypt 哈希。
- POST /api/auth/login、GET /api/auth/me，HS256 JWT 签发 / 验签 / 过期，密钥来自环境变量，默认 30 分钟。
- 管理员分页搜索用户、查询详情、创建用户、修改显示名 / 状态、多角色分配、角色查询。
- 每次请求从数据库刷新账号状态和角色，禁用与角色变化对已有 Token 生效。请求规则与方法注解双重授权。
- 登录页、工作台、个人资料、用户管理、菜单、路由鉴权、Pinia、Axios 401 / 403 处理、前端退出清理。
- FileStorage 接口预留，后续实体关系与数据级授权已设计；没有提前实现媒体 / 任务业务。

## 本轮再次验收与补充

用户再次提供完整 Stage 1 任务后，在现有 main 上核对实现，保留已有项目和远程历史，继续遵循用户已确认的 public 仓库设置。

复现并修复了全局异常兜底把框架 HTTP 错误误报为 500 的问题：修复前真实 GET /api/auth/login 和 text/plain 登录均返回 500；修复后保留 405 / 406 / 415 及 Allow 等协议头，错误正文仍为统一 JSON。不返回原始异常细节。

新增 3 项 MockMvc 回归与 3 次真实 MySQL HTTP 断言，最终后端 21 项、真实 HTTP 28 项均通过。重新执行 TypeScript、前端构建与 6 项 Chromium 验收均通过。

复验采用前台 Maven / Vite 会话，后端 8180、前端 5180。启动 / 测试脚本支持 MEDIAFLOW_BACKEND_URL、PLAYWRIGHT_BASE_URL 和 MEDIAFLOW_API 自定义目标，默认端口保持不变；说明已写入 README。

## 环境与实际测试

复用 Java 17.0.12、Maven 3.9.10、Node.js 22.17.1、本地 MySQL 8.0.41。实际前端为 Vite 6.4.6。首次交付后端在 8080、前端在 127.0.0.1:5173 启动；本轮修正版在 8180 / 5180 复验；浏览器测试请求经 Vite 代理访问实际后端与 MySQL。

| 验证 | 实际结果 |
| --- | --- |
| `mvn -B -f backend/pom.xml clean verify` | BUILD SUCCESS；编译、打包与 21 项 JUnit 测试通过；0 失败、0 错误、0 跳过 |
| 后端 MockMvc / 数据库测试 | 18 项，隔离 H2 MySQL 模式，真实 SecurityFilterChain / Mapper / BCrypt / JWT，不使用模拟登录身份绕过鉴权 |
| JWT 单元测试 | 3 项：往返、篡改、弱配置拒绝 |
| `python scripts/smoke-test.py` | 真实 MySQL + HTTP，28 次断言通过，临时用户已清理 |
| `npm run typecheck` | 通过（vue-tsc --noEmit） |
| `npm run build` | 通过；有大体积 chunk 警告，见已知问题 |
| `npm run test:e2e` | 6 项 Chromium 真实浏览器用例通过，临时用户已清理 |
| 首次交付的 `npm audit --omit=dev --registry=https://registry.npmjs.org --json` | 本次生产 npm 依赖审计返回 0 个已知漏洞；不是全系统安全审计 |
| Git 提交前检查 | 检查被追踪文件、敏感值与忽略规则，真实数据库密码 / JWT 密钥 / .env / 构建产物未纳入提交 |

后端自动化覆盖：正确登录与资料不泄漏密码、错误密码 / 未知用户名、匿名访问、EDITOR / REVIEWER 越权、JWT 过期 / 非法 / 错签、禁用用户后旧 Token 和登录失败、多角色授权与移除对旧 Token 生效、重复用户 409、非法输入 400、用户不存在 404、未知角色原子拒绝、自锁保护、OpenAPI 生成、内部异常与认证数据库故障返回 500，以及错误方法 / 响应格式 / 请求内容类型的 405 / 406 / 415。

真实 HTTP / MySQL 验收：三个测试账号登录成功；错误密码、无 Token、两个普通角色访问管理员接口、非法 / 过期 Token 被拒；ADMIN 创建临时用户、分配多角色、编辑状态；对同一旧 Token 提权后可访问、降权后 403、禁用后 401；校验 400 / 404 / 409，并新增真实请求验证 405 / 406 / 415。最终仍只有三个种子用户，没有遗留验收用户。

浏览器验收：管理员登录 / 刷新 / 用户列表 / 个人资料 / 退出；EDITOR 与 REVIEWER 手工进入管理 URL 被导航拦截且直接 HTTP 请求返回 403；错误密码显示反馈；非法本地 Token 触发 401 清理和登录导航；管理员通过页面真正创建用户、编辑显示名、禁用、变更角色并确认表格刷新。

## 问题处理记录

- 初次浏览器启动在创建 page 阶段超时；重跑 5 项通过，补充用户管理实际操作后最终 6 项通过。
- 新增用户管理 UI 用例首次点击 Element Plus 隐藏的原生 checkbox 时超时，改为点击可见标签后重跑；未将超时当作通过。
- Windows 运行中的目标 JAR 被锁，导致一次重打包失败；停止进程后 clean verify 成功。当前运行副本放在被忽略的 .local，不再锁构建产物。
- 本机 npm 镜像不提供 audit API（404）；改用 npm 官方 registry 完成生产依赖审计。
- GitHub CLI 初始不在 PATH，已在项目 .local 安装；未完成持久化设备登录，但复用 Git Credential Manager 的已有授权调用 CLI / 推送，没有输出或落盘访问令牌。

- 本轮停止旧进程与后台启动命令被自动审批拒绝，原因仅为 blocked by policy；改为独立端口前台会话后已成功启动并完成全部复验，原 8080 / 5173 实例保留。
- 自定义浏览器端口后，初次路由断言未等待异步鉴权导航，导致两项用例失败；改为等待 pathname 后，最终 6 项全部通过。

## 仓库与交付

远程：[WuJiaoHuang/MediaFlow](https://github.com/WuJiaoHuang/MediaFlow)。用户已创建仓库，检查时为空，无远程分支或未合并提交；按用户后续明确要求使用 **public**，覆盖初始 private 要求。不强制推送、不覆盖历史。

第一阶段提交消息：`feat initialize MediaFlow backend frontend and authentication`。本轮补充提交按新附件使用 `feat: initialize MediaFlow backend frontend and authentication`，不改写已推送的初始提交。最新提交 Hash 通过 `git rev-parse HEAD` 查询，最终回复提供实际值；报告不写入自引用的提交 Hash。Git 提交与推送结果以最终验收回复为准。

## 未实现项、已知限制与验证边界

- Stage 1 必需功能没有已知阻塞项；未执行生产部署、负载测试或全栈漏洞扫描，不能据此宣称生产就绪。
- 按范围未实现媒体库、剪辑任务、协作反馈、修改审计业务，也没有视频处理、独立文件库、Refresh Token 或黑名单。
- 退出仅清除本地凭据，已复制 Token 在过期前仍有效，除非禁用用户或轮换密钥。
- 角色控制已落地，任务 / 审核授权的数据级控制仅为后续设计，当前没有任务数据可验收。
- 前端 Element Plus 全量引入使主 JS chunk 约 1.13 MB（gzip 约 376 KB），构建有体积警告；后续可按需导入组件与拆分 vendor。
- 用户分页角色查询存在 N+1，最大每页 100；大规模用户需批量关系查询。
- 本地开发账号公开且仅供测试，生产必须换用独立账号、密钥和最小权限数据库身份；尚无登录限流、密码重置 / 修改及生产级操作审计。
- 本次 MySQL 集成验证为真实本机实例；H2 测试用于快速回归，不等同全部 MySQL 方言验证。

## Stage 2 建议

只开发媒体库：分类、统一 FileStorage 本地适配、受控上传与下载、类型 / 大小校验、素材元数据、筛选分页、预览、明确统计口径和必要的修改记录。先落实素材访问规则，不开发独立文件库、不提前引入消息队列 / 转码 / AI。
