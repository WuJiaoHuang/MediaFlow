# MediaFlow Stage 4 验收报告

日期：2026-11-21（Asia/Shanghai）。基线 main / 166d9d5be1f346e344553721ba49e57c39e142f4。开始时工作区干净，读取 README、Stage 3 报告、任务授权、相关前后端代码与迁移，未发现 AGENTS.md，没有覆盖用户未提交修改。沿用当前仓库与 main，不创建新仓库、不强制推送。

## 交付功能

- 任务内 TEXT / AUDIO / VIDEO 反馈；可关联本任务素材或整个任务，音视频可带文字说明。文字必填，正文 / 回复最多 4000 字，操作人仅取服务端认证身份。
- 时间码为可空非负毫秒，仅允许关联音视频素材；界面显示时间位置，链接携带任务上下文，原播放器 loadedmetadata 后定位。没有可靠服务端时长元数据，未声称验证上限。
- 任务详情新增协作区域，按素材 / 类型 / 状态过滤、创建时间升降序、稳定分页与未解决数量；反馈详情显示提出人、时间、附件播放器与下载、回复、处理状态和完整历史。刷新通过真实 API 恢复。
- 浏览器 MediaRecorder 实际录音 / 录像，支持开始、停止、回放、重新录制；录制端音频 120 秒、视频 60 秒；不支持 / 设备权限拒绝 / 失败时仍可选择本地文件。显示上传进度、取消、失败原因与重试。离开组件释放设备、计时器、ObjectURL，避免重复开始。
- 默认音频 10 MiB（MP3 / WAV / M4A / WebM），视频 25 MiB（MP4 / WebM）；独立于普通媒体 100 MiB。校验文件名、大小、真实内容、MIME 与扩展名；WebM 校验 DocType / TrackType，区分音频与视频并拒绝不完整 Tracks；MP4 / M4A 检查容器品牌与 soun / vide handler。没有引入解码、转码或 AI。
- 独立附件元数据与授权入口，复用 FileStorage / 随机 UUID 键，不插入 media_asset / media_file，不污染媒体统计，不替换原素材，没有独立文件库。
- 创建幂等键防重复，回复和反馈状态变更均带 version；任务行锁 → 反馈行锁 → 版本条件 UPDATE，日志同一事务。任务提交和审批共享任务锁，使反馈与任务流转不会相互穿透。
- 没有反馈 / 回复 / 日志的任意编辑、删除接口。反馈引用的任务素材关联禁止移除，其余关联差异更新；修改任务要求、改派与追加素材仍可进行。归档不删除历史意见。

## 状态与授权

完整设计见 [feedback-workflow.md](feedback-workflow.md)。

反馈：OPEN / REOPENED → RESOLVED → CLOSED；RESOLVED / CLOSED → REOPENED，重开必须有原因。当前执行人 / ADMIN 处理；仍有当前任务授权的作者、当前审核人 / ADMIN 确认或重开。CLOSED 回复前需先重开。

任务：PENDING / COMPLETED 下所有反馈写操作禁止；PROCESSING 允许当前成员提出意见，REVIEWING 仅当前审核人 / ADMIN 提出意见；两个可写阶段都允许按身份回复、处理、确认、重开，因此不存在“只有审核中才能确认但又无法提交”的死锁。

普通任务提交阻止 OPEN / REOPENED，响应含未解决数量；RESOLVED 可提交、等待审核人确认。ADMIN 例外提交必须显式填写 1..1800 字原因，任务日志 SUBMIT_OVERRIDE 记录数量及原因。审批必须全部 CLOSED，没有管理员静默审批例外。任务退回保留原意见状态，COMPLETED 不重开。

|身份|实际读取边界|写操作边界|
|---|---|---|
|ADMIN|所有任务、反馈、附件、历史|可写阶段与对应状态内代办；例外提交必须有原因|
|EDITOR|仅当前分配且仍具有 EDITOR 角色的任务|PROCESSING 提出；可写阶段回复与处理；作者确认权仍需当前任务授权|
|REVIEWER|仅当前指定且仍具有 REVIEWER 角色的任务|提出、回复、确认、带原因重开；无执行人身份不能标记处理|
|多角色|有效授权并集|每个动作按具体身份检查|
|无关用户 / 旧参与者|404|不得通过任何父子 ID 或旧凭据访问|

附件独立 purpose=feedback JWT 绑定 task / feedback / attachment，仅用于 GET/HEAD content/download，120 秒 HttpOnly / Strict / HTTPS Secure Cookie。每次仍读取用户状态 / 角色并检查当前任务及父子关系。改派 / 撤销角色 / 禁用后，旧 Cookie 的后续 Range、下载与续签分别拒绝；普通素材 LIBRARY 可读不等于反馈附件可读。凭据不能作为 Bearer、跨反馈使用或访问其他 API。storageKey 不返回给客户端。

## 数据库迁移与实际运行

真实本机 MySQL **8.0.41** 上执行 V004__task_feedback.sql 成功，第二次执行同样成功。仅新增 task_feedback / task_feedback_reply / task_feedback_attachment / task_feedback_log；有必要的用户 / 任务 / 反馈外键、任务素材复合外键、幂等与附件唯一约束、状态 / 类型 / 时间码 CHECK 和查询索引。没有 DROP / TRUNCATE、重建旧表、清空库、修改其他项目数据库或重置账号密码。

新版后端实际运行 `http://127.0.0.1:8280`，Vue 前端 `http://127.0.0.1:5280` 代理到该后端；测试使用真实 MySQL，H2 只用于自动化后端测试。没有以旧实例替代新代码验收。

## 最终验证结果

|命令 / 验证|结果|
|---|---|
|mvn -B -f backend/pom.xml clean verify|87 tests，0 failures / errors / skipped，BUILD SUCCESS|
|frontend: npm run typecheck|通过|
|frontend: npm run build|通过，Vite 生产构建完成|
|frontend: npm run test:e2e|10 passed（约 1.1 分钟，含原有 8 项回归与新增 2 项）|
|python scripts/feedback-smoke-test.py|104 项真实 HTTP / MySQL 检查通过|
|python scripts/task-smoke-test.py|97 项 Stage 3 真实 HTTP / MySQL 检查通过|
|python scripts/media-smoke-test.py|12 项 Stage 2 真实 HTTP / MySQL 检查通过|
|python scripts/smoke-test.py|28 项 Stage 1 真实 HTTP / MySQL 检查通过|
|V004 初次 / 重复执行|均成功|

后端分组：AuthenticationIntegrationTest 18、EditingTaskIntegrationTest 14、FeedbackIntegrationTest 23、FeedbackMediaValidatorTest 5、JwtServiceTest 3、LocalFileStorageTest 5、MediaIntegrationTest 16、MediaTypeValidatorTest 3，总计 87。保留原有 59 项测试，新增 28 项。

反馈覆盖：创建 / 查询 / 回复 / 可信操作人、四态合法及非法转换、重开必填原因、PENDING / COMPLETED 只读、CLOSED 禁止直接回复、提交 / 审批门槛和 ADMIN 留痕、双角色动作隔离、过滤分页和重试幂等、跨用户 / 任务 / 素材 / 反馈 / 附件越权、撤权后旧 Cookie、角色撤销与禁用、真实录制音视频容器、错误 MIME / 扩展名 / 内容 / 大小 / 文件名、Range 与用途绑定、同版本并发一次 200 一次 409、日志失败回滚状态 / version、文件写入失败回滚数据库、附件元数据失败清理本次文件、关联素材删除约束、实际任务音视频时间码、归档后历史保留。

故障注入用例会产生预期 ERROR 日志，最终聚合仍为零失败；没有删除既有测试。文件系统部分写入清理由原 LocalFileStorageTest 回归，反馈上传额外验证数据库失败后清理新对象，不删除既有媒体文件。

真实 HTTP 使用 ADMIN 与运行时随机密码的两个 EDITOR、两个 REVIEWER 验证完整业务：提出 / 回复 / 处理 / 确认 / 重开、管理员例外提交留痕、审批阻止未确认、退回保留 CLOSED、最终 COMPLETED 只读、改派撤权、共享原素材仍可读但反馈附件不可读、角色撤销 / 禁用后原 Cookie 无效、真实 MySQL 并发冲突。记录为检查项数量，包含一次并发结果断言，不等同单独请求数。

浏览器录制使用 Chromium 的模拟麦克风 / 摄像头作为输入，实际调用 getUserMedia + MediaRecorder 编码 WebM，实际加载并播放，再通过真实后端上传 / 预览；并非 Mock Blob 或伪造成功。生成样本保存在 .local/feedback-recorded-{audio,video}.webm，离线容器测试附带同源录制样本。权限拒绝、不支持 MediaRecorder 与网络中断通过明确的前端错误注入验证降级 / 重试，不能当作真实 OS 权限面板或物理硬件验收。

浏览器业务：ADMIN 建任务并开始 → REVIEWER 创建文字 / 实际录音 / 实际录像 / 文件备选 → EDITOR 被未解决数量拦截提交 → 查看并回复 → 标记处理 → REVIEWER 确认、重开并填写原因 → 再处理 → 提交审核 → 审批被待确认数量拦截 → 逐一确认 → 审批完成 → 刷新与只读验证。正文 XSS 样本作为普通文本显示。Stage 1 登录 / 路由 / 账号管理、Stage 2 媒体完整流程和 Stage 3 改派 / 审核浏览器用例继续执行。

验收数据按返回的本次 ID 和经过格式检查的存储键清理，不批量清空业务表。调试中断的本次浏览器任务也逐条核验来源后清理；最终核对：保留原有 3 个用户，所有媒体 / 分类 / 审计 / 任务 / 反馈业务表均恢复验收前的 0 行，本次验证存储目录 .local/media-storage 中 0 个 .bin 文件。没有清空已有业务记录。

本地证据（不提交运行产物）：.local/stage4-maven-verify.log、stage4-typecheck.log、stage4-build.log、stage4-all-e2e.log、stage4-http-results.json、stage4-http.log、stage4-task-http.log、stage4-media-http.log、stage4-auth-http.log、stage4-cleanup-results.json，以及 Playwright JSON 报告。每次重跑会覆盖相应本地结果；可用仓库中的命令与脚本复验。

## 主要文件

- V004__task_feedback.sql，测试 feedback-schema.sql、feedback-fixtures。
- FeedbackController、FeedbackService、TaskFeedback / Reply / Attachment / Log 实体与 Mapper，FeedbackRequest / ReplyRequest / Query。
- FeedbackMediaValidator；MediaTypeValidator 复用文件名校验；JwtService / JwtAuthenticationFilter 增加绑定反馈用途。
- EditingTaskService / Mapper 增加反馈门槛和安全的素材关联差异更新。
- feedback.ts、FeedbackPanel / Composer / Recorder、FeedbackDetailView、TaskDetailView / TaskFormView / MediaDetailView、路由与 Playwright 设备参数。
- FeedbackIntegrationTest、FeedbackMediaValidatorTest、frontend/tests/feedback.spec.ts、scripts/feedback-smoke-test.py。
- README、architecture、database-design、security、feedback-workflow、本报告，.env.example / application.yml 独立附件大小参数。

## 未验证边界与后续

- 没有可靠服务端时长，未执行时间码上限或导入文件时长校验；录制时长上限是浏览器 UI 约束，大小限制由后端强制执行。
- MP3 / WAV / M4A / MP4 的内容识别是探测与容器检查，不等同完整解码、杀毒或保证每个浏览器可播放所有编码。Chromium 实际录制 / 播放验收覆盖 WebM Opus、VP8/Opus；真实物理麦克风 / 摄像头、Safari / Firefox、移动端和生产 HTTPS 环境未验收。
- 进程在写盘后崩溃或回滚清理遇到 IO 故障，仍可能留下孤立文件；没有跨文件系统 / MySQL 原子事务，未声称崩溃恢复或生产文件对账已实现。
- 撤权不收回此前下载 / 缓冲的数据，也不中断已在发送的响应；后续请求实时重新授权。退出登录仍为本地令牌清理，短期预览 Cookie 边界见安全文档。
- Stage 5 可继续文件对账、生产监控 / 备份恢复、跨业务审计和更多统计；本次不提前实现清理平台、微服务、消息中间件、实时聊天、在线剪辑、转码或 AI。

最终提交哈希与推送结果见交付回复；也可通过 `git log -1 -- docs/stage4-report.md` 查询本报告所属提交。
