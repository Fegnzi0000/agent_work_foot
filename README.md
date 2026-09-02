# AI 干饭搭子后端

面向微信小程序用户端和网页管理员端的后端服务。当前项目以单体应用方式运行：小程序与管理网页共用同一套后端、数据库和认证体系，但接口权限与业务边界相互隔离。

## 当前范围

| 端 | 当前职责 |
| --- | --- |
| 微信小程序用户端 | 注册登录、用户资料、饮食记录、转盘、统计等用户功能。 |
| 网页管理员端 | 管理员登录、数据看板、用户查询与状态管理、管理员操作审计。 |

管理员端当前只管理普通用户数据，不提供管理员账号的网页管理功能。

## 权限模型

项目采用两级角色语义：

- `USER`：普通小程序用户。
- `ADMIN`：网页管理员，可访问 `/api/v1/admin/**` 接口并管理普通用户。

不再使用 `SUPER_ADMIN` 或多层级 RBAC。对于当前两人开发、管理员数量较少的阶段，这样更直接，也避免了尚无实际需求的角色和权限配置复杂度。

## 已实现的管理员功能

- 管理员邮箱密码登录、JWT 鉴权和退出登录。
- 管理首页统计：用户、饮食记录、转盘记录、近七日趋势等。
- 用户列表：按邮箱、昵称、用户状态、注册日期范围筛选并分页。
- 用户详情与用户状态更新。
- 管理员操作审计查询。
- 开发环境跨域白名单：默认只允许 `http://localhost:5173` 的管理网页访问。

接口字段、请求示例和前端使用约定以 `E:\work\gpt_work\前端开发说明.md` 为准。

## 技术栈

- Java 25
- Spring Boot 4.1、Spring Security
- MyBatis-Plus、MyBatis XML
- MySQL 8.4
- Flyway（数据库结构迁移）
- JWT（管理员与用户认证）
- Maven

## 数据库与迁移

数据库结构仅由 Flyway 管理，迁移文件位于：

```text
src/main/resources/db/migration
```

当前迁移为：

```text
V1__initial_schema.sql
V2__add_admin_login_name.sql
```

本地开发处于可重新初始化阶段时，可以清空现有表后重新启动服务，让 Flyway 按顺序执行迁移创建完整结构。之后发生任何真实的表结构或初始数据变更，都必须新增新的版本文件，不能修改已经在其他环境执行过的迁移。

## 本地启动

### 前置条件

- JDK 25
- Maven 3.9+
- MySQL 8.4（可使用本机安装的 MySQL；Docker 仅在运行集成测试时需要）
- 已创建本地数据库并完成连接配置

开发环境配置在 `src/main/resources/application-dev.yaml`。默认数据库连接为本机 MySQL 的 `3307` 端口；如本机配置不同，请按实际情况调整本地配置，不要提交账号密码。

PowerShell 示例：

```powershell
$taskJavaHome = 'E:\java-jdk25-temurin'
$env:JAVA_HOME = $taskJavaHome
$env:Path = "$taskJavaHome\bin;$env:Path"
Set-Location E:\work_java\agent_work_foot\agent_work_foot
mvn spring-boot:run
```

默认服务地址：

```text
http://127.0.0.1:8080
```

健康检查：

```text
GET /actuator/health
```

### 管理网页联调

管理网页项目位于：

```text
E:\work\gpt_work\front\admin
```

启动前端真实接口模式：

```powershell
Set-Location E:\work\gpt_work\front\admin
npm.cmd run dev:api
```

然后通过以下地址访问：

```text
http://localhost:5173
```

注意前端应使用 `localhost`，不要改成 `127.0.0.1:5173`。浏览器跨域规则将两者视为不同来源；当前后端开发环境白名单只放行 `http://localhost:5173`。

## 开发环境 SQL 日志

`dev` Profile 已启用 MyBatis SQL 诊断日志。每次 Mapper 查询或更新会记录：

- Mapper 方法标识；
- 归一化后的 SQL；
- 参数摘要；
- 查询行数或更新影响行数；
- 执行耗时与异常类型。

密码、令牌、授权信息等敏感字段会在摘要中脱敏。该诊断拦截器仅在 `dev` 环境加载，测试和生产环境不会输出这类详细日志。

## 本地联调数据

本地已准备过管理员和演示用户数据时，可使用：

| 类型 | 账号 | 密码 | 说明 |
| --- | --- | --- | --- |
| 管理员 | `admin` | `123456` | 网页管理员账号；关联邮箱为 `admin@local.test`，仅限本地开发数据库。 |
| 演示用户 | `demo.user01@local.test` 至 `demo.user12@local.test` | `User_123` | 包含正常、禁用、注销等状态及关联业务数据。 |

这些账号不是 Flyway 的固定种子数据；重新清空数据库并执行 V1 后，需要按本地联调数据脚本或说明重新写入。完整数据范围和 SQL 见：

```text
E:\work\gpt_work\管理员网页本地联调数据.md
```

## 将已有用户提升为管理员

管理员账号不提供公开注册入口。先通过正常注册流程创建用户，再在本地使用一次性引导 Profile 将指定邮箱提升为 `ADMIN`：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev,bootstrap-admin'
$env:APP_ADMIN_BOOTSTRAP_EMAIL = '已注册用户邮箱'
$env:APP_ADMIN_BOOTSTRAP_ACCOUNT = '管理员登录账号'
mvn spring-boot:run
```

该 Profile 以非 Web 方式运行，完成提升后会退出。管理员账号为 3 至 32 位字母、数字或下划线，且以字母开头；它不接受角色参数。

生产环境应通过受控运维流程执行，不应暴露为普通 HTTP 接口。

## 生产环境要点

- 使用 `prod` Profile，并通过环境变量提供数据库连接和 JWT 密钥。
- 必填数据库变量：`AGENT_WORK_FOOT_DB_URL`、`AGENT_WORK_FOOT_DB_USERNAME`、`AGENT_WORK_FOOT_DB_PASSWORD`。
- 必填 JWT 变量：`APP_AUTH_JWT_ACTIVE_KEY_ID`、`APP_AUTH_JWT_ACTIVE_SECRET`。
- 将 CORS 白名单改为实际管理网页域名，不使用 `*` 通配符。
- 保持 Flyway 启用，并让 Hibernate 只校验结构，不自动建表或改表。

## 验证与测试

仅检查编译：

```powershell
mvn test -DskipTests
```

运行完整测试：

```powershell
mvn test
```

完整测试包含 Testcontainers 集成测试，需要本机 Docker 已启动；未启动 Docker 时相关测试无法执行，这是测试环境条件，不代表应用无法启动。

## 交接文档

项目开发与联调说明集中在 `E:\work\gpt_work`，避免混入后端源码目录：

- `E:\work\gpt_work\前端开发说明.md`：前端接口、字段和联调约定。
- `E:\work\gpt_work\后端开发说明.md`：后端架构、实现方案、配置和开发约定。
- `E:\work\gpt_work\开发进度.md`：当前已完成内容、待办与后续阶段。
- `E:\work\gpt_work\管理员网页本地联调数据.md`：管理员网页本地演示数据说明。
