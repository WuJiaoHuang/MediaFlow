# MediaFlow

企业内部媒体资产与剪辑协作管理系统。Stage 1 完成认证与账号权限；Stage 2 新增媒体库、分类、真实统计、字段级修改记录以及受控预览下载。
系统没有独立文件库，不包含剪辑任务、协同反馈、转码或 AI 业务。

## 技术栈与环境

- Java 17、Maven 3.6.3+、Spring Boot 3.5.7、Spring MVC / Security、Nimbus JWT、Jakarta Validation。
- MyBatis-Plus 3.5.12（包含独立 JSQLParser 分页依赖）、MySQL 8.0、springdoc 2.8.14、JUnit 5。
- Node.js 22 LTS、npm、Vue 3、TypeScript、Vite 6、Element Plus、Axios、Vue Router、Pinia。
- 不依赖 Redis、消息队列或微服务中间件。`frontend/package-lock.json` 锁定实际安装版本。

## 目录

```text
backend/src/main/java/com/mediaflow/
  common/ config/ controller/ dto/ entity/ mapper/ security/ service/ storage/ vo/
backend/src/main/resources/      # 环境变量配置
backend/src/test/                # JUnit / MockMvc / H2 测试
frontend/src/                    # Vue 页面、路由、状态、请求层
frontend/tests/                  # Playwright 真实浏览器验收
sql/schema.sql                  # 独立 mediaflow 数据库和三张表
sql/data.sql                    # 仅本地开发种子数据（BCrypt）
scripts/                        # 启动和真实 MySQL HTTP 验收脚本
docs/                           # 架构、数据库、安全、验收报告
```

## 初始化 MySQL

请使用独立的本地 MySQL 实例或账号。脚本仅创建 `mediaflow`，不修改其他数据库。

```powershell
mysql -u root -p --default-character-set=utf8mb4
```

在 MySQL 客户端执行（从项目根目录启动客户端）：

```sql
source sql/schema.sql;
source sql/data.sql;
source sql/migrations/V002__media_management.sql;
```

`schema.sql` 的 IF NOT EXISTS 用于重复初始化，不承担后续版本迁移；未来结构变化需单独迁移脚本。`data.sql` 不覆盖已有用户密码、状态或已有角色；不要在生产环境导入开发测试数据。

复制 `.env.example` 为项目根目录的 `.env`，填写 `DB_USERNAME`、`DB_PASSWORD` 与随机生成的 `JWT_SECRET`。MySQL 默认连接本机 3306；其他地址可设置 `DB_URL`。生成密钥：

```powershell
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

`.env`、日志、构建产物、上传文件均被忽略；真实配置不进入仓库。Spring Boot 本身不会自动读取 `.env`，下面的 PowerShell 启动脚本会加载它；其他系统需在启动进程前自行导出同名环境变量。不要在公开日志中输出环境变量。

## 启动

后端（根目录）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1
```

或先将 `.env` 中的值设置为进程环境变量，再运行：

```sh
cd backend
mvn spring-boot:run
```

前端（另一个终端）：

```sh
cd frontend
npm ci
npm run dev
```

打开 [前端](http://127.0.0.1:5173)，后端默认 [http://localhost:8080](http://localhost:8080)，[Swagger UI](http://localhost:8080/swagger-ui.html)。Vite 将 `/api` 代理到 `127.0.0.1:8080`，开发无需跨域；自定义后端地址可设置前端进程环境变量 `MEDIAFLOW_BACKEND_URL`（默认 `http://127.0.0.1:8080`）。前端支持刷新恢复登录，同一标签页通过 sessionStorage 保存短期 Token。

## 仅本地开发测试账号

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| admin | Admin@123456 | ADMIN |
| editor01 | Editor@123456 | EDITOR |
| reviewer01 | Reviewer@123456 | REVIEWER |

**以上为公开的开发测试账号，禁止用于生产环境。** 数据库只存 BCrypt 哈希（成本 12），开发种子 SQL 不含明文密码。正式环境必须单独创建账号和独立签名密钥。

## 主要接口

所有响应为 `{ "code": 0, "message": "success", "data": ... }`，失败使用对应 HTTP 状态与非零业务码。错误方法 / 响应格式 / 请求内容类型分别保留 HTTP 405 / 406 / 415，405 同时返回 Allow 头。

| 方法 | 路径 | 权限 / 行为 |
| --- | --- | --- |
| POST | /api/auth/login | 匿名；用户名密码换取 accessToken、expiresIn 和 user |
| GET | /api/auth/me | 已登录；当前用户资料及实时角色 |
| GET | /api/admin/users?page=1&size=10&keyword= | ADMIN；分页 / 搜索，size ≤ 100 |
| GET | /api/admin/users/{id} | ADMIN；用户详情 |
| POST | /api/admin/users | ADMIN；创建账号、BCrypt 密码、多角色，HTTP 201 |
| PUT | /api/admin/users/{id} | ADMIN；修改 displayName 与 status（0/1） |
| PUT | /api/admin/users/{id}/roles | ADMIN；以 roles 数组整体替换角色，至少一个 |
| GET | /api/admin/roles | ADMIN；可用角色列表 |

退出登录仅在前端清除凭据；没有服务端登出撤销接口。所有用户响应均排除 passwordHash。创建用户的密码限制为 12–64 位 ASCII 可见字符，避免 BCrypt 72 字节截断歧义。

## 验证

```sh
mvn -f backend/pom.xml clean verify
cd frontend
npm ci
npm run typecheck
npm run build
npx playwright install chromium
npm run test:e2e
```

JUnit 使用隔离 H2 MySQL 模式运行，无需真实凭据；覆盖认证、权限、禁用、角色刷新、参数校验和异常。浏览器测试需要真实前后端运行、本地种子账号、根目录 `.env` 和 PATH 中的 MySQL CLI；用户管理测试创建唯一临时用户并在 finally 中清理。

真实 MySQL 环境的 HTTP 集成验收（根目录，需 Python 3、MySQL CLI、`.env`，以及正在运行的后端）：

```sh
python scripts/smoke-test.py
```

该脚本只创建唯一命名的临时用户，并在 finally 中通过 MySQL CLI 清理；它验证真实 BCrypt 登录、越权、角色变化和禁用，结果写入被忽略的 `.local/smoke-results.json`。当前脚本按本地 `localhost` / `mediaflow` 设计，不用于远程或生产验收。

完整证据、重跑说明及边界见 [Stage 1 报告](docs/stage1-report.md)。

## Stage 2 媒体库

已存在 Stage 1 数据库时，仅运行 `sql/migrations/V002__media_management.sql` 升级，不重建数据库或用户表。迁移可重复执行，但不负责修正手工改变的已有表结构。

正常素材暂对所有已登录用户可读。ADMIN 上传、编辑全部素材、管理分类及归档；EDITOR 上传并只编辑本人素材；REVIEWER 只读。归档仅 ADMIN 可读。任务级数据隔离尚未实现。

上传通过内容检测、MIME 和扩展名一致性检查；支持 JPEG / PNG / WebP、MP3 / WAV / M4A、MP4 / WebM，默认单文件 100 MB。STORAGE_ROOT 指定本地存储根目录，默认相对后端运行目录的 uploads。不得将它配置为反向代理静态目录。文件流经受控接口访问，不能通过 storage_key 直接读取。

浏览器预览凭据为单素材、只读、120 秒有效的 HttpOnly / SameSite=Strict Cookie，详情页每 90 秒续签；没有在 URL 中放 JWT。详情、下载和每个 Range 请求仍检查当前用户和素材状态。

页面：`/media` 媒体库、`/media/:id` 详情、`/media/categories` 分类管理（ADMIN）、`/media/stats` 统计、`/media/changes` 修改记录。工作台显示真实统计。

浏览器核心验收（包括原有 6 项 Stage 1 回归）：`npm run test:e2e`。脚本使用程序生成的 PNG / WAV / MP4，创建唯一临时分类和素材，结束时仅清理本次生成的数据与文件。自定义 STORAGE_ROOT 时，将相同绝对路径设置为 E2E 进程的 MEDIAFLOW_STORAGE_ROOT。

HTTP 媒体边界验收：根目录运行 `python scripts/media-smoke-test.py`，验证重复提交、非法文件 / 路径、真实 MySQL 事务回滚、排序 / 日期校验及请求体大小上限。自定义运行目标时配置 MEDIAFLOW_API 和 MEDIAFLOW_STORAGE_ROOT，与 E2E 相同。

详细流程与接口：[媒体管理](docs/media-management.md)、[审计](docs/media-audit.md)、[Stage 2 报告](docs/stage2-report.md)。

## 后续阶段

- Stage 2 已完成媒体素材管理，后续按真实使用情况优化。
- Stage 3：剪辑任务创建、分配及按参与者限制数据访问。
- Stage 4：文字 / 语音 / 视频协作反馈与审核授权。
- Stage 5：扩展到跨业务审计、更多统计指标与生产运维加固。

[架构](docs/architecture.md) · [数据库设计](docs/database-design.md) · [安全](docs/security.md)

## 并行启动与自定义验收端口

如默认端口已有实例，可在 `.env` 中设置 `SERVER_PORT=8180` 后启动另一后端；不需要停止其他进程。前端在另一终端运行：

```powershell
cd frontend
$env:MEDIAFLOW_BACKEND_URL='http://127.0.0.1:8180'
npm run dev -- --port 5180
```

浏览器验收设置 `$env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:5180'` 后运行 `npm run test:e2e`。HTTP 验收在根目录设置 `$env:MEDIAFLOW_API='http://127.0.0.1:8180'` 后运行 `python scripts/smoke-test.py`。这些值只控制本地目标地址，不包含凭据；省略时仍使用 8080 / 5173。
