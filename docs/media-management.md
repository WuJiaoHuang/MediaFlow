# 媒体管理

## 存储与上传流程

media_file 是存储元数据，不提供独立文件库页面或任意 key 读取 API。media_asset 是可搜索、分类、归档的业务实体；当前一个素材对应一个不可覆写文件。LocalFileStorageService 实现 FileStorage，未来可替换实现。归档保留文件和外键，暂不提供物理删除 / 替换接口。

上传流程：请求级和方法级校验 ADMIN / EDITOR → DTO / 请求字段校验 → 分类存在与有效状态检查 → 检查同用户 Idempotency-Key → Tika 只按内容探测 → 扩展名与声明 MIME 对照 → 随机 UUID.bin、CREATE_NEW 流式写盘并校验实际长度 → 注册事务回滚文件清理 → 写入 media_file / media_asset → 写入 CREATE 字段日志 → 同事务提交。

仅引入 tika-core 内容探测，不引入解析器包、FFmpeg、外部存储或中间件。Tika 将 WebM 初步识别为 Matroska 时，额外解析 EBML 头的 DocType，只有 webm 才允许 video/webm。ISO MP4 被初步识别为 QuickTime 时，只接受服务端白名单内的 ftyp 主品牌并规范为 video/mp4；qt 主品牌不能靠改名通过。支持的 MIME / 扩展名映射在 MediaTypeValidator 中集中定义。内容检测检查格式签名而非完整编解码验证，损坏或浏览器不支持的编码会展示预览错误，不声称做过杀毒或转码。

同用户上传键唯一约束处理并发重复请求；已完成的重复提交返回 409，前端保留同一键用于失败重试。不按内容 hash 去重，不把合法的不同上传当成重复。文件复制失败删除已创建的部分文件；数据库或日志事务失败由 afterCompletion 清理本次新文件。CREATE_NEW 失败不会清理原有同名文件。

文件系统与 MySQL 不是分布式事务：进程在写盘和事务完成之间被强制终止、磁盘清理失败时，仍可能残留孤立对象；清理失败记录不透明 key，需人工对照 media_file 清理，不可盲删 uploads。未做进程崩溃恢复或杀毒验证。

## 分类与归档

分类最多两级、全局名称唯一。写入前锁定分类集合，校验父节点、两级深度、自引用与子节点约束，避免成环。停用分类不隐藏已有关联素材，禁止分配新素材；上级停用也禁止分配子级。原分类停用后仍可修改素材其他字段。删除分类必须没有子分类且没有任何关联素材，包括归档素材。历史日志保存分类 ID 和当时名称快照，分类改名 / 删除不改写历史。

素材状态 ACTIVE / ARCHIVED。默认仅 ACTIVE；仅 ADMIN 可以查询 ARCHIVED 或 ALL，以及访问归档详情、文件与历史。只有 ADMIN 可以归档。归档幂等，重复归档不新增日志。无恢复或物理删除按钮，后续引用关系接入前不擅自删除文件。

## 查询与统计口径

列表由 MyBatis-Plus Page 在数据库分页，size 1–100。支持 keyword、categoryId、mediaType、uploadedBy、from、to、status、sort、direction。categoryId=0 为未分类，分类筛选是精确分类，不自动包含子分类；时间范围为包含两端的素材创建时间，from 不得晚于 to。

排序仅允许 createdAt / title / fileSize 与 asc / desc；以 id DESC 作为同值稳定排序。动态 SQL 只使用白名单分支，不拼接前端字段。名称采用 SQL LIKE 模糊查询，% / _ 保留 LIKE 通配符语义。

统计和列表共用 MediaAssetMapper.xml 的 filters SQL，权限校验相同。COUNT(*) 以素材为单位；JOIN 关系均一对一，不 JOIN 多角色或审计明细，避免重复计数。类型数为 SUM(CASE WHEN media_type=... THEN 1 ELSE 0 END)，最近新增为同一过滤范围内 created_at >= 当前时间减 7 天。文件总量为 SUM(file_size)。分类分布以 category_id 精确 GROUP BY；补齐数据库分类集合中无匹配素材的零值，并明确一行未分类。分类停用不改变已有素材计数。归档不纳入默认统计，只有管理员显式筛选时纳入。

统计在只读事务中执行，在 MySQL 默认 REPEATABLE READ 下总量和分组使用一致快照。前端出现请求错误时清空统计展示，显示错误与重试，不用 0 冒充成功；SQL 正常返回的空集合才显示真实 0。

## 主要接口

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| POST | /api/media/assets | multipart：file、title、description、categoryId；必须提供 Idempotency-Key，成功 201 |
| GET | /api/media/assets | 分页 / 筛选 / 排序 |
| GET | /api/media/assets/{id} | 详情，无 storage_key |
| PUT | /api/media/assets/{id} | title、description、categoryId、version；所有权与版本检查 |
| POST | /api/media/assets/{id}/archive | ADMIN 归档 |
| POST | /api/media/assets/{id}/access | Bearer 鉴权后颁发短期预览 Cookie，返回受控 URL |
| GET / HEAD | /api/media/assets/{id}/content | 受控预览，支持 Range / 206 |
| GET / HEAD | /api/media/assets/{id}/download | 附件下载，支持 Range |
| GET | /api/media/categories | 已登录分类列表，含停用分类 |
| POST / PUT / DELETE | /api/media/categories[/{id}] | ADMIN 创建、编辑（排序 / 状态 / 层级）、受限删除 |
| GET | /api/media/stats | 与列表相同过滤条件 |
| GET | /api/media/changes | 审计分页 / 素材 / 操作人 / 时间范围 |

错误使用原统一响应和 HTTP 状态。新增 41300 / HTTP 413 处理大小上限；内容 / MIME 不匹配为 415，重复上传 / 版本冲突 / 关联分类不可删为 409。二进制响应不包装 JSON，设置 MIME、nosniff、private/no-store 与安全的 Content-Disposition 文件名。

## 浏览器预览

原生 img / audio / video 无法在每次 Range 请求上附加应用的 Bearer 头。详情页先用 Bearer 调用 access，服务端签发用途为 preview、绑定 asset ID、120 秒有效的专用 JWT Cookie，HttpOnly / SameSite=Strict，路径限定到单素材。过滤器仅在该素材 content/download 的 GET/HEAD 接受它；不能用于管理接口、编辑、其他素材或换取登录身份。每个请求仍读取实时用户状态和角色，并检查素材当前可读性。

页面每 90 秒用 Access Token 续签，原生播放器直接流式加载受控 URL，不将整段视频转成内存 Blob。通过认证后，FileSystemResource 由 Spring MVC 处理 HTTP Range。Cookie 在 HTTPS 请求时标记 Secure；反向代理部署必须正确设置可信转发头与 HTTPS。凭据和二进制不写公共缓存。

前端退出仍清理 Access Token；已发出的单素材凭据在最多 120 秒内有效，这与已下载 / 已缓冲内容无法收回不同，需要明确理解。禁用用户或归档素材会立即拒绝后续请求。尚未实现最终任务级数据隔离。

实现依据：[Spring MVC Range](https://docs.spring.io/spring/reference/web/webmvc/mvc-range.html)、[Tika 3.2.3 内容检测](https://tika.apache.org/3.2.3/api/org/apache/tika/Tika.html)。实际兼容性以本项目的回归与真实浏览器验收为准。
