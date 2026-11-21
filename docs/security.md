# 安全设计与边界

## 认证

BCrypt 成本 12；创建用户密码为 12–64 位 ASCII 可见字符（单字节），登录最多 64 位，避免 BCrypt 超过 72 字节的截断 / 异常。数据库只保存哈希。未知用户也执行一次 BCrypt 校验以降低用户名枚举的时间差。

JWT 由 Spring Security OAuth2 JOSE 的 Nimbus 实现 HS256 签发和验证。密钥仅来自 JWT_SECRET，启动要求至少 32 字节；部署使用密码学安全随机值。Token 含 issuer=mediaflow、sub=用户 ID、iat、exp，没有密码、显示名称或角色。
默认有效期 1800 秒，支持 1–3600 秒；验证时间不允许额外宽限。错误签名、非法格式、缺失时间声明、错误 issuer、过期均拒绝。

每个受保护请求重新查询数据库中的用户状态和角色；禁用账号、用户删除、角色修改在后续请求生效，不使用过期的 JWT 角色缓存。已经通过鉴权且正在执行的请求不会被中途撤销。

无 Refresh Token、黑名单、Redis 或服务端 Session。前端退出清理 sessionStorage 与 Pinia，已复制的 Token 仍可使用至过期；紧急撤销可禁用账号，密钥轮换会让所有旧 Token 失效。

## 授权与输入

请求级 `/api/admin/**` 规则与管理员 Controller 的方法级 `@PreAuthorize` 防止普通用户调用管理功能。前端路由和菜单只改善体验，不替代服务端授权。
管理操作只接收白名单 DTO；不能通过注入 id、passwordHash 或任意角色字段修改数据库。参数校验与分页上限在服务端执行。MyBatis 参数绑定避免 SQL 拼接。
角色集合在删除旧关系前完整验证，替换在事务中执行并锁定用户行。管理员不能禁用自身或移除自身 ADMIN，避免常见自锁。

HTTP 错误与业务码一致：40000 参数、40100 未登录 / 凭据无效、40300 无权限、40400 不存在、40900 冲突、50000 内部错误。内部异常、数据库异常不返回 SQL 或堆栈。框架的 405 / 406 / 415 不再被误报为 500，保留 Allow 等协议头，错误正文仍使用统一 JSON 且不回显原始异常信息。
Swagger 开发环境可匿名读取接口描述，实际业务仍需 Bearer 认证；正式部署可通过 SPRINGDOC_API_DOCS_ENABLED=false 和 SPRINGDOC_SWAGGER_UI_ENABLED=false 关闭。

## 浏览器与部署

Token 保存在当前标签页 sessionStorage，刷新可恢复，关闭标签页清除；不设长期本地持久化。XSS 仍可能读取 Token，页面不使用 v-html 展示输入。不要在 URL、日志、监控标签或 Git 文件中放 Token。开发使用 Vite 同源代理，后端无开放 CORS；生产使用同源反向代理与 HTTPS。

当前为 Stage 1–3 开发基线。生产上线前还需替换所有公开测试账号、使用最小权限数据库账号、TLS、反向代理限流 / 登录失败限制、密钥管理、CSP、备份恢复演练和依赖漏洞检查。当前没有登录速率限制、密码修改 / 找回或跨业务完整操作审计（已实现媒体字段审计），未声称可直接用于生产。

角色权限不等于数据权限：未来任务查询 / 修改 / 下载必须同时检查当前身份的分配关系或审核授权；管理员例外应显式定义，并确保统计与导出使用同样的过滤条件。

## 本地配置与仓库

`.env`、`.local`、日志、构建产物、node_modules、上传目录均排除提交。用户提供的数据库凭据仅用于本地 `.env` 与运行进程，不进入仓库或报告。公开的开发账号密码仅在 README 与本地验收代码中出现，种子 SQL 只有 BCrypt 哈希。


## Stage 2 文件与媒体权限

写操作同时受请求级 SecurityConfig 与方法注解保护。EDITOR 的素材更新还在 Service 查询锁定记录后检查 created_by 与认证身份；请求体中的 operatorId / createdBy 不参与授权和审计。REVIEWER 不可上传、修改、归档或管理分类。分类写入仅 ADMIN。预览与下载先以素材 ID 授权，再从数据库取得内部 storage_key，不接受前端任意路径。

Stage 2 的 ACTIVE 全员可读现保留为 LIBRARY；Stage 3 的 RESTRICTED 仅对 ADMIN / 上传者 / 当前有效任务参与者可读。ARCHIVED 仍仅 ADMIN。列表、统计、审计和二进制入口均应用当前矩阵，不能把共享素材声称为私有。

媒体内容先由 tika-core 探测，与允许的 MIME 和扩展名一起校验；WebM 与 ISO MP4 额外检查容器头。拒绝 HTML / SVG / 任意二进制伪装，限制大小，拒绝路径字符和控制字符。UUID.bin 与 CREATE_NEW 防覆盖，root 规范化、拒绝文件软链接，不将本地目录暴露为静态资源。内容探测不等同杀毒或完整解码校验。

浏览器专用预览 JWT 只用于单素材 content/download 的 GET / HEAD；purpose=preview + asset 绑定，不能作为 Bearer Access Token 使用，不能越到其他素材。Cookie 有效 120 秒，HttpOnly / SameSite=Strict，HTTPS 下 Secure。前端每 90 秒用正常 Bearer 续签；Access Token 不写 URL。每个 Cookie 请求仍检查数据库当前状态和素材权限，禁用 / 归档后新 Range 请求也会拒绝。

退出登录仍为本地 Access Token 清理。已发出的预览凭据最多继续有效 120 秒，不提供服务端黑名单；此前已下载或缓存到播放器内的数据不能收回。这一边界没有被表述为即时全局登出撤销。

审计接口只读，字段值通过 Vue 文本插值显示，不用 v-html。类别名称、操作者名称和素材标题快照不包含密码 / JWT。上传异常返回明确 HTTP 413 / 415 / 409；不能向客户端泄漏存储路径、SQL 或堆栈。事务失败清理新文件，物理清理失败 / 强制进程终止的残留需运维对账，尚未验证崩溃恢复。

## Stage 3 数据权限与撤销

完整矩阵及拒绝策略见 task-authorization.md。任务角色范围是当前有效角色与当前分配的交集，多个角色取并集；写动作再检查执行 / 审核身份，不能因为 REVIEWER 可读就允许开始剪辑。管理员创建 / 编辑 / 人员选择接口对普通用户统一 403，不论任务存在与否；对象级详情、动作、关联素材、历史无任务授权为 404。有任务读权限但动作角色不足为 403。

受限素材的所有入口统一使用当前权限：列表、详情、统计、媒体审计、content/download、预览凭据续签。SQL 使用 EXISTS 当前任务关系，删除关联或重新分配、角色变更后不依赖过期 Token 缓存。ADMIN 可显式改变素材范围且需 version，范围变更写入原媒体审计，保留原结构。

任务 Cookie 有 task claim，并在名称中区分任务；taskId query 仅为上下文标识。不能把 task Cookie 去掉 taskId 后用作基础媒体凭据，不能更换 task 或 asset。签名合法且尚未过期也必须再次查询任务 / 关联 / 媒体状态。已授权且正在流式传输的响应不能中途收回已发送字节；后续请求重新鉴权。共享 / 上传者 / 其他当前任务的独立基础授权仍有效，这不是撤销绕过。

操作日志身份来自 CurrentUser，不信任 operatorId；前端以文本插值展示日志与要求，避免注入 HTML。状态不由任意请求字段直接设置，只接受四个明确动作。事务 / 行锁 / 带旧版本条件的 UPDATE 防止重复流转和并发覆盖；日志失败回滚任务或分配，不开独立日志事务。

受限素材正常状态访问不自动授予媒体编辑权，EDITOR 仍仅可编辑本人上传的素材。任务授权没有增加上传目录静态映射、任意 storage_key 访问、服务端 Token 黑名单或 Stage 4 反馈功能。

## Stage 4 反馈与附件授权

矩阵见 feedback-workflow.md。反馈对象授权只从当前任务继承，旧作者 / 上传者身份没有额外读取权。无任务授权、跨 task / feedback / asset / attachment 父子关系统一 404；有读权限但具体写身份不足 403；状态与 version 冲突 409；非法参数 400；文件大小 413；内容不匹配 415。列表只在授权任务内 SQL 分页。

附件预览 JWT 使用 purpose=feedback，绑定 task / feedback / attachment。Cookie 名 mf_feedback_{attachmentId}，Path 限定该附件，HttpOnly / SameSite Strict / HTTPS Secure，有效 120 秒。过滤器仅在反馈 content/download 的 GET/HEAD 使用，不能作 Bearer、访问普通媒体或其他 API，不能变换父级 ID；每次重新读取用户状态 / 角色，再检查当前任务权限和附件父子关系。媒体 LIBRARY、RESTRICTED 和原媒体预览 Cookie 不能授予反馈附件权。Range / 下载 / 续签没有旁路。

正文 / 回复最多 4000 字，重开原因最多 2000 字，ADMIN 例外提交原因最多 1800 字并记录未解决数量，所有身份取 CurrentUser。Vue 文本插值渲染，禁止 v-html。原始文件名拒绝路径与控制字符；存储键不序列化给客户端，不接收客户端 storageKey；复用本地 UUID/CREATE_NEW 安全存储，目录不得静态暴露。

文件内容探测和容器结构检查不等同完整解码或杀毒；未引入时长提取。已下载 / 缓冲的数据不能远程撤回；撤权作用于后续请求，单次正在发送的响应不强制中断。数据库回滚清理新文件已经通过故障注入验证；进程崩溃和清理 IO 失败仍可能留下孤立文件，没有声称文件系统与 MySQL 原子提交，也没有实现 Stage 5 清理平台。
