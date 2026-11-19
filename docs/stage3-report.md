# MediaFlow Stage 3 验收报告

日期：2026-11-19（Asia/Shanghai）。基线 c5e529967d71e91458dc3cf6ca645f896c48587c，main 分支。开始时工作区干净，未发现 AGENTS.md，没有覆盖其他人的未提交修改。

## 已实现并验证

- ADMIN 创建 / 编辑任务：标题、剪辑要求、一个或多个正常媒体、当前执行人、审核人、优先级、可空截止时间、状态、时间戳及 version。
- 校验用户存在 / 启用 / 当前角色；执行人必须 EDITOR，审核人 REVIEWER 或 ADMIN；关联素材至少一份、最多 50 份，不可重复或归档。
- 状态机只允许 PENDING → PROCESSING → REVIEWING → COMPLETED，以及 REVIEWING → PROCESSING；退回原因必填。当前执行人开始 / 提交，当前审核人通过 / 退回，ADMIN 按同样状态规则代办。COMPLETED 不编辑、不重开。
- 每次改动持有行锁并比较 version，UPDATE 再携带旧版本条件。分配、字段修改、状态动作与日志同事务；日志失败回滚业务，重复同版本动作只有一次成功。
- 数据级任务权限：ADMIN 全部；当前 EDITOR 执行人和当前 REVIEWER 审核人取并集；有读权限不代表有所有写动作。列表在 SQL 过滤，EXISTS / 单行关系不会造成分页重复。
- 任务详情、关联素材、状态动作和历史逐次鉴权。无任务授权为 404；ADMIN 专属功能对普通用户始终 403（不论 ID 是否存在），不以状态差异泄漏任务存在性；有任务读权限但动作身份不足为 403。
- 单独的任务日志保留创建、执行人 / 审核人 / 优先级 / 截止时间 / 要求 / 标题 / 素材关联变化、开始、提交、通过、退回。操作人来自 CurrentUser，旧历史不因重新分配被删除。没有改写原媒体字段审计表。
- 前端任务列表、ADMIN 创建编辑、详情、素材预览下载、状态按钮、退回弹窗、操作历史均接入真实 API。处理加载、空页、401 / 403 / 404 / 409，刷新从服务端恢复状态。
- 已替换此前运行的 Stage 2 后端，当前服务运行新版授权，避免旧运行实例成为绕过入口。

## 主要新增 / 修改文件

| 范围 | 文件 |
| --- | --- |
| 迁移 | sql/migrations/V003__editing_tasks.sql |
| 任务 Controller / Service | EditingTaskController、EditingTaskService |
| 统一对象授权 / SQL | AuthorizationService、AccessContext、AuthorizationMapper.xml |
| 实体 / Mapper | EditingTask、EditingTaskAsset、EditingTaskLog、EditingTaskMapper / XML、EditingTaskLogMapper |
| DTO / VO | TaskRequest、TaskActionRequest、TaskQuery、TaskView、TaskPerson、AssetScopeRequest |
| 既有媒体入口 | MediaService、MediaController、MediaAssetMapper、MediaChangeLogMapper、AssetView、MediaAsset |
| 预览 | JwtService、JwtAuthenticationFilter，绑定可选 task claim 并逐次重新授权 |
| 前端 | tasks.ts、TaskListView、TaskFormView、TaskDetailView、MediaDetailView、路由 / 导航、HomeView |
| 验收 | EditingTaskIntegrationTest、tests/tasks.spec.ts、scripts/task-smoke-test.py |
| 文档 | README、architecture / database-design / security、task-authorization.md、本报告 |

## 迁移与运行

V003 已在真实本机 MySQL 8.0.41 执行成功，并重复执行验证。新增 editing_task、editing_task_asset、editing_task_log；media_asset 仅增加 access_scope，默认 LIBRARY，保留旧素材基础策略。没有 DROP / TRUNCATE / 重建 Stage 1/2 表，没有重设账号密码、清空原库或修改其他项目数据库。

当前后端 8280，前端 5280；源码默认 8080 / 5173 不变。沿用 Java 17.0.12、Maven 3.9.10、Node.js 22.17.1、Vue / Element Plus、本地 FileStorage，没有新增中间件或物理文件副本。

```powershell
# 根目录：当前本地 .env 未固定 SERVER_PORT 时可设置进程变量；否则修改 .env 对应值。
$env:SERVER_PORT='8280'
$env:STORAGE_ROOT=Join-Path (Get-Location).Path '.local/media-storage'
powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1

# 另一终端
cd frontend
$env:MEDIAFLOW_BACKEND_URL='http://127.0.0.1:8280'
npm run dev -- --port 5280
```

## 权限边界（先设计、后改造）

完整设计见 task-authorization.md。未把 Stage 2 全员共享素材静默改成仅任务参与者可见。

| 对象 / 上下文 | 实际边界 |
| --- | --- |
| 任务 | ADMIN 全部；当前有效 EDITOR 执行人 / REVIEWER 审核人取并集 |
| LIBRARY 正常素材 | 登录用户可独立通过媒体库读取，不宣称为私有 |
| RESTRICTED 正常素材 | ADMIN、上传者、当前有效关联任务参与者可读 |
| 归档素材 | ADMIN 可读；其他用户不可读 |
| 指定任务中的素材 / 凭据 | 必须有该任务授权且当前关联；独立共享 / 上传者权限不能绕过指定任务上下文 |
| 媒体写入 | ADMIN 或本人上传的 EDITOR；任务读授权不额外授予编辑素材权限 |

ADMIN 在媒体详情显式切换素材访问范围，带 version 并写入原媒体审计。列表、详情、统计、修改历史、预览、下载、续签都使用统一授权服务 / 同一 SQL 可读片段。重新分配 / 取消素材关联 / 角色变化后，新的请求重新检查当前数据，不依赖 JWT 中的旧授权。

任务 Cookie 绑定 asset 和 task，名字区分任务，URL 只有 taskId 标识；不能改 taskId、替换 assetId，或去掉任务上下文后继续使用。有效期内撤销授权也返回 404。共享 / 上传者 / 其他当前任务产生的独立基础授权仍可存在，这是明确的基础授权，不被误说为任务私有隔离。

## 实际测试结果

| 验证 | 结果 |
| --- | --- |
| mvn -B -f backend/pom.xml clean verify | BUILD SUCCESS，59 项通过，0 失败 / 错误 / 跳过 |
| 既有后端回归 | 原 45 项保留并通过，覆盖 Stage 1 认证 / 用户 / JWT 与 Stage 2 媒体 / 存储 / 审计 |
| 新增任务集成测试 | 14 项通过，真实 SecurityFilterChain / Mapper / 事务，隔离 H2 MySQL 模式 |
| 前端类型检查 | vue-tsc --noEmit 通过 |
| 生产构建 | vite build 通过；最大 JS chunk 约 250.36 KB，保留 Stage 2 按需导入与分块 |
| 全部 Playwright | 8 项通过（39.8 秒），含原 7 项和新增任务多角色场景 |
| 任务浏览器场景复验 | 1 项再次通过（14.9 秒），增加等待真实历史加载后截图 |
| 真实 HTTP / MySQL 任务检查 | 97 项通过，含独立客户端并发请求；并发用例作为一项检查记录两次请求状态 [200,409] |
| 真实旧接口回归 | Stage 1 smoke 28 项 + Stage 2 media smoke 12 项通过 |
| 迁移 | MySQL 首次与重复执行成功，原有账号仍可登录 |

### 后端任务测试覆盖

有效创建、至少一份 / 重复 / 归档 / 不存在素材、非法标题 / 优先级、错误角色 / 禁用 / 不存在用户；普通用户不能创建或自行分配；两个不同执行人和两个不同审核人的隔离；详情 / 素材 / 日志 / 状态动作统一对象授权；完整通过与退回流程、空退回原因拒绝、完成后不可修改 / 重开；客户端伪造 operatorId 无效；字段实际变化与无变化日志；多角色并集且分页无重复；角色撤销即时生效；审计失败时创建与状态回滚；同版本并发一次成功；组合筛选 / 排序 / 分页仍受 SQL 权限限制。

受限素材重新分配后，测试旧执行人的列表、详情、统计、显式修改记录、文件、下载和续签均失去授权；普通单素材 Cookie 与任务 Cookie 在有效期内同样受约束。另有共享素材测试：独立媒体库仍可读，但被撤销任务上下文的凭据不可继续访问。

### 真实 HTTP / MySQL 验收

scripts/task-smoke-test.py 使用真实种子账号登录，并通过管理员接口创建临时 EDITOR B、REVIEWER B、EDITOR+REVIEWER 用户，随机密码不写源码或输出。上传管理员持有的共享 / 受限素材，创建两位执行人的任务，证明列表分离和无关任务 404。

执行 PENDING → PROCESSING → REVIEWING →（带原因退回）PROCESSING → REVIEWING；管理员在待审核状态重新分配执行人 / 审核人并调整要求、优先级、截止时间，清空截止时间也实际验证。旧人员失去任务及受限媒体的所有新请求授权，包括已经签发的 Cookie；新审核人通过成为 COMPLETED，尝试重开 / 修改 409，旧操作和原因仍在历史中。

另一个任务通过两个独立 HTTP 客户端竞争相同 version，实际结果 [200,409]；继续正常提交 / 审核。验证多素材关联、实际组合筛选 / 排序 / 数据库分页、多角色三种分配组合不重复、角色变化撤销对应任务 grant、共享策略与任务上下文分离、重复素材 / 无效人员 / 归档素材拒绝、匿名 401。没有用 H2 代替这些 MySQL 验收。

### 浏览器真实业务

ADMIN 在媒体详情页面把共享素材设为受限 → 页面创建任务并选择已有素材 / 执行人 / 审核人 / 优先级 → 另一执行人的独立任务用于隔离对照 → EDITOR A 的列表只见自己的任务，EDITOR B / 无关审核人访问 A 的任务 404 → A 从任务进入素材页，图片真实加载、任务 Cookie 的 Range 206 → A 开始 / 提交 → 审核人空原因退回被阻止，再填写原因退回 → A 刷新看到 PROCESSING 并再次提交 → ADMIN 用页面重新分配执行人 / 审核人 → 旧 A 的有效 Cookie 值未变且仍未过期，文件请求却返回 404，直接素材 / 统计 / 修改日志 / 续签同样拒绝 → 新执行人可看但不能以执行人身份审批 → 新审核人页面通过 → 完成状态、编辑按钮消失、历史保留原执行人与退回原因。全过程用真实 API、MySQL、浏览器，不使用业务 Mock。

## 资源清理与提交安全

真实脚本与 E2E 只按本次 API 返回的临时 ID 删除关联 / 日志 / 任务，再清理本次媒体和随机账号；物理 key 先检查 UUID 格式，只删除对应生成文件。没有递归删除目录或删除原账号。提交前已复查：原有 admin / editor01 / reviewer01 均启用且原角色保留，用户总数 3；三张任务表和四张媒体表均无残留测试记录，本次验收物理文件数为 0。工具日志、截图、随机凭据、.env、上传文件、构建和缓存均在忽略目录。

Git 仓库沿用现有 origin / main，不创建新仓库，不强制推送；先 fetch 核对是否出现远程新增提交。提交前检查全部索引文件 UTF-8、真实本地密码 / JWT 密钥 / Token / 构建产物，最终 Hash 与推送核对见交付回复（报告不写入自引用 Hash）。

## 修复记录

首次将可空 task claim 直接加入 JWT builder 导致旧媒体凭据签发失败；改为只有 taskId 非空才加 claim，原 Stage 2 预览测试恢复并通过，任务凭据仍严格校验上下文。没有删除或弱化旧测试来适配新功能。

## 风险、未验证项与未完成范围

- 共享 LIBRARY 的独立可读范围是刻意保留的策略；只有 RESTRICTED 的无独立 grant 用户才会随任务撤销完全失去素材基础访问。上传者仍保留其素材基础读权限。需要私有隔离必须显式设置受限，不自动推断。
- 无法收回已经下载、缓冲或正在传输的字节；后续 HTTP / Range 请求重新授权。退出仍采用原本地清理，未引入服务端黑名单。
- 使用当前一个执行人 / 一个审核人的模型，允许有效双角色用户被同时指定；没有额外强制职责分离规则。修改分配保持原状态，不静默跳状态。
- 素材后续归档不自动改变任务状态；普通参与者不能读取归档文件，ADMIN 仍可查看，非已完成任务可按正常管理流程替换关联素材。
- 未执行生产部署、压力测试、分布式并发或全量安全审计；本轮验证为本机 MySQL 和 Chromium。既有文件系统崩溃恢复、编解码 / 转码限制仍保留。
- Stage 3 必需功能没有已知阻塞项；协同反馈、语音 / 视频意见、消息通知、复杂时间轴、转码 / AI、独立文件库、任意任务重开均未提前实现。

## Stage 4 建议

围绕任务 / 作品的文字、语音、视频协同反馈，复用任务授权与统一存储。定义反馈接收人、线程 / 处理状态、时间码及附件安全；把相关读取、提交、下载和统计同样绑定任务授权，独立迁移并新增事务审计与真实多角色验收，不扩大到在线视频剪辑或中间件平台。
