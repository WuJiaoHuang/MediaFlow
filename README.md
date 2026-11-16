# MediaFlow

企业内部媒体资产与剪辑协作管理系统。Stage 1 完成单体基础架构、MySQL 用户权限模型、登录认证和真实角色鉴权，以及可运行的管理前端。
本阶段没有媒体库、文件库、剪辑任务、反馈、转码或 AI 业务实现。

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

打开 [前端](http://127.0.0.1:5173)，后端默认 [http://localhost:8080](http://localhost:8080)，[Swagger UI](http://localhost:8080/swagger-ui.html)。Vite 将 `/api` 代理到 `127.0.0.1:8080`，开发无需跨域；更改后端端口时同步修改 Vite 代理。前端支持刷新恢复登录，同一标签页通过 sessionStorage 保存短期 Token。

## 仅本地开发测试账号

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| admin | Admin@123456 | ADMIN |
| editor01 | Editor@123456 | EDITOR |
| reviewer01 | Reviewer@123456 | REVIEWER |

**以上为公开的开发测试账号，禁止用于生产环境。** 数据库只存 BCrypt 哈希（成本 12），开发种子 SQL 不含明文密码。正式环境必须单独创建账号和独立签名密钥。

## 主要接口

所有响应为 `{ "code": 0, "message": "success", "data": ... }`，失败使用对应 HTTP 状态与非零业务码。

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

## 后续阶段

- Stage 2：媒体素材分类、统一存储适配、受控上传、筛选分页、预览；不建设独立文件库。
- Stage 3：剪辑任务创建、分配及按参与者限制数据访问。
- Stage 4：文字 / 语音 / 视频协作反馈与审核授权。
- Stage 5：修改前后值审计、统计口径治理及生产运维加固。

[架构](docs/architecture.md) · [数据库设计](docs/database-design.md) · [安全](docs/security.md)
