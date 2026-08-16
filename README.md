# AI 干饭搭子后端

面向微信小程序的饮食记录、食物池和随机推荐后端。当前按**模块化单体**部署：一个 Spring Boot 应用、一个 MySQL 数据库；不使用微服务、消息队列、Redis 或 Agent/AI 服务。

## 已实现功能

- 认证与账号：邮箱注册/登录、JWT Access Token、Refresh Token 轮换、退出、改密、注销、请求 ID 和可配置内存限流。
- 用户与偏好：首次引导、昵称、预算历史、预设与自定义饮食偏好。
- 食物池：默认食物初始化、分页筛选、新增、修改、标签、软删除和重复保护。
- 饮食记录：从食物池或手动输入创建记录、可选加入食物池、历史查询、修改、软删除和消费统计。
- 老虎机：基于当前有效食物池等概率抽取、结果快照、5 分钟有效期、重转、幂等确认并生成饮食记录。
- 管理端：数据库 RBAC、用户查询、启用/禁用、一次性临时密码和审计日志；`ADMIN` 仅管理普通用户，`SUPER_ADMIN` 可管理其他管理员。

所有金额在 HTTP 请求和响应中都是十进制字符串，响应固定两位小数，例如 `"18.50"`。后端业务计算使用 `BigDecimal`，MySQL 使用 `DECIMAL`。

## 技术栈

| 范围 | 方案 |
| --- | --- |
| 运行时 | Java 25、Spring Boot 4.1、Maven |
| Web 与校验 | Spring MVC、Spring Validation、Jackson |
| 安全 | Spring Security、Nimbus JOSE、HS256 JWT、Refresh Token 哈希与轮换 |
| 数据访问 | MyBatis-Plus 3.5.17、Mapper XML、MySQL Connector/J |
| 数据库迁移 | Flyway、MySQL 8.4 |
| 测试 | JUnit 5、Spring MVC Test、Testcontainers MySQL 8.4 |
| 运维 | Spring Boot Actuator、Docker |

## 架构与模块

```text
微信小程序 / 管理员前端
        │ HTTPS / JSON
        ▼
Controller → Service → Mapper → MySQL 8.4
                  │
                  ├─ Spring Security + JWT + RBAC
                  ├─ Flyway
                  ├─ 限流、统一异常、requestId
                  └─ Actuator
```

每个业务模块均遵循 `Controller → Service → Mapper`：

| 模块 | 职责 |
| --- | --- |
| `common` | 统一响应、异常、字段错误、requestId、金额字符串处理 |
| `config` | Security、Jackson、MyBatis-Plus、时钟、环境属性 |
| `auth` | 注册、登录、JWT、Refresh Token 与认证限流 |
| `user` | 当前用户资料、改密、注销 |
| `preference` | 引导、预算历史、偏好预设和用户偏好 |
| `food` | 默认食物模板、用户食物池、标签和软删除 |
| `diet` | 饮食记录快照、历史查询和统计 |
| `slot` | 随机抽取、Spin 生命周期与幂等确认 |
| `rbac` | 角色、权限及认证主体权限解析 |
| `admin` | 管理员用户操作、临时密码、审计、一次性管理员提升 |

Mapper 只负责数据访问。单表操作优先使用 `Entity + BaseMapper`；分页、标签 AND 筛选、行锁、统计等复杂查询位于 Mapper XML；全部参数使用 `#{...}` 预编译绑定，禁止 `${...}`。

## 关键数据流

### 认证与用户归属

```text
注册/登录 → 签发 Access JWT + Refresh Token
          → Security Filter 校验 JWT、加载角色权限
          → Security Context 中取得当前用户 ID
          → Controller/Service 仅操作该用户归属的数据
```

客户端不传入可决定数据归属的 `userId`。Access Token 使用 `kid` 支持当前密钥与上一把密钥平滑轮换；Refresh Token 仅保存 SHA-256 摘要，刷新时撤销旧令牌并创建新令牌。

### 注册初始化

```text
创建 users
  → 复制 food_default_templates 到 food_options / food_option_tags
  → 创建 refresh_tokens
  → 返回 ONBOARDING
```

以上步骤由认证服务的同一事务提交或回滚。

### 食物、记录与老虎机

```text
Food 管理 → food_options + food_option_tags
                 │
Diet 手工/选池 ───┼→ diet_records（名称、分类、标签、金额全部保存快照）
                 │
Slot 抽取 ───────┴→ slot_spins（食物快照）
Slot 确认 → 同一事务创建 source=SLOT 的 diet_records 并标记 CONFIRMED
```

历史记录只读取 `diet_records` 快照，不因食物池后续修改或删除而变化。食物和记录均为软删除；统计只聚合未删除的饮食记录。老虎机确认使用行锁和 `confirmed_diet_record_id` 保证幂等。

## 物理数据模型

Flyway 在空库上按顺序执行 `V1`、`V2`、`V3`：

| 领域 | 表 |
| --- | --- |
| 用户与会话 | `users`、`refresh_tokens`、`temporary_passwords` |
| 管理审计 | `admin_audit_logs` |
| 偏好与预算 | `preference_presets`、`preference_items`、`user_budget_histories` |
| 食物池 | `food_default_templates`、`food_options`、`food_option_tags` |
| 饮食与随机 | `diet_records`、`slot_spins` |
| RBAC | `roles`、`permissions`、`role_permissions` |

主键使用应用层生成的 UUID 字符串。时间以 UTC `DATETIME(3)` 持久化；饮食记录另存按 `Asia/Shanghai` 计算的 `business_date`。数据库结构的唯一来源是 [db/migration](src/main/resources/db/migration)。

## 本地部署

### 前置条件

- JDK 25
- Maven 3.9+
- Docker Desktop
- 可用的 MySQL 8.4 容器

### 启动本地 MySQL

PowerShell：

```powershell
docker run --name agent-work-foot-mysql `
  --restart unless-stopped `
  -e MYSQL_ROOT_PASSWORD=RootLocal_2026 `
  -e MYSQL_DATABASE=agent_work_foot `
  -e MYSQL_USER=agent_app `
  -e MYSQL_PASSWORD=AgentLocal_2026 `
  -p 3307:3306 `
  -v agent-work-foot-mysql-data:/var/lib/mysql `
  -d mysql:8.4 `
  --character-set-server=utf8mb4 `
  --collation-server=utf8mb4_0900_ai_ci
```

项目的 `application-dev.yaml` 默认连接 `localhost:3307`。启动应用时 Flyway 会自动创建空库的结构和预设数据：

```powershell
$env:JAVA_HOME = 'E:\java-jdk25-temurin'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

服务默认地址为 `http://127.0.0.1:8080`，接口前缀为 `/api/v1`，健康检查为 `GET /actuator/health`。

### 生产部署

生产环境不提交数据库账号、密码或 JWT 密钥。设置以下环境变量后，以 `prod` Profile 启动：

```text
SPRING_PROFILES_ACTIVE=prod
AGENT_WORK_FOOT_DB_URL=jdbc:mysql://<host>:3306/<database>?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
AGENT_WORK_FOOT_DB_USERNAME=<database-user>
AGENT_WORK_FOOT_DB_PASSWORD=<database-password>
AGENT_WORK_FOOT_JWT_ACTIVE_KEY_ID=<active-key-id>
AGENT_WORK_FOOT_JWT_ACTIVE_SECRET=<long-random-secret>
AGENT_WORK_FOOT_JWT_PREVIOUS_KEY_ID=<previous-key-id-or-empty>
AGENT_WORK_FOOT_JWT_PREVIOUS_SECRET=<previous-secret-or-empty>
```

生产必须使用 HTTPS，并在微信公众平台配置对应的合法请求域名；不要把开发环境的数据库密码或 JWT 密钥用于生产。

构建并运行生产 Jar：

```powershell
mvn clean package
java -jar target/agent_work_foot-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

## 测试账号与管理员账号

数据库迁移**不会写入任何可登录的用户或管理员账号**，也不保存明文密码。因此不存在可以从仓库安全提供的“默认管理员密码”。下面是一套可重复使用的本地测试约定：

| 身份 | 邮箱 | 密码 | 创建方式 |
| --- | --- | --- | --- |
| 普通测试用户 | `test.user@example.com` | `Testuser_2026` | 调用注册接口或在小程序注册 |
| 管理员测试用户 | `test.admin@example.com` | `Testadmin_2026` | 先按普通用户注册，再执行下方提升命令 |

上述是**建议的本地测试凭据，不是预置账号**。首次使用必须注册；请勿在生产环境使用。

将已注册的管理员测试用户提升为 `SUPER_ADMIN`：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev,bootstrap-admin'
$env:APP_ADMIN_BOOTSTRAP_EMAIL = 'test.admin@example.com'
$env:APP_ADMIN_BOOTSTRAP_ROLE = 'SUPER_ADMIN'
mvn spring-boot:run
```

该命令不启动 HTTP 服务，完成提升后会自动退出；只允许把已注册且状态为 `ACTIVE` 的 `USER` 提升为 `ADMIN` 或 `SUPER_ADMIN`。提升会撤销该用户已有 Refresh Token，随后请重新登录。

## 测试

```powershell
$env:JAVA_HOME = 'E:\java-jdk25-temurin'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn test
```

测试使用 Testcontainers 启动隔离的 MySQL 8.4，并验证 Flyway、Mapper、认证、权限、食物池、饮食记录和老虎机 HTTP 契约。运行完整测试前需确保 Docker Desktop 的 daemon 对当前终端可访问。

仅编译主代码和测试代码：

```powershell
mvn test -DskipTests
```

## 相关文档

产品、接口和数据库文档与本工程分开维护在工作区的 `E:\work\gpt_work`：

- `PRD.md`：产品需求。
- `docs/openapi.yaml`：机器可读接口契约。
- `docs/API.md`：前后端联调说明。
- `database.md`：字段与表设计说明。
- `开发日志.md`：阶段记录与后续规划。
