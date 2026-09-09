# 生产部署与恢复基线

本文给出可提交到仓库的部署基线。真实域名、证书、数据库地址和密钥必须由部署环境注入，不写入 Git、镜像或前端产物。

## 1. 每一项的作用

| 能力 | 作用 | 缺失时的直接风险 |
|---|---|---|
| `prod` Profile | 把开发数据库、调试日志和本地 CORS 与生产环境隔离 | 生产服务可能误连本机配置或启用开发行为 |
| Docker 镜像 | 固定 Java 25、构建方式和启动命令，使本机、流水线和云端使用同一运行单元 | 云端环境与开发机不一致，出现“本机能跑、部署失败” |
| HTTPS 域名 | 为小程序提供可登记的安全 API 地址，并在传输中保护 Token 和业务数据 | 正式小程序不能请求 HTTP/IP 地址，明文链路也不安全 |
| 环境变量/密钥管理 | 将密码、JWT、AppSecret 与代码和镜像分离，支持独立轮换 | 密钥进入 Git 后难以撤销，多个环境会互相污染 |
| 备份与恢复流程 | 证明数据库不仅“备份成功”，而且真的可以恢复 | 只有备份文件、没有恢复验证，故障时仍可能不可用 |

## 2. 构建与容器

仓库根目录的 `Dockerfile` 使用官方 Maven/Temurin 25 镜像构建，运行阶段使用非 root 用户。

```bash
docker build -t agent-work-foot:2026-09-07 .
docker run --rm -p 8080:8080 --env-file deploy/production.env agent-work-foot:2026-09-07
```

`deploy/production.env.example` 只能复制为本机临时模板；生产值应填写到微信云托管或腾讯云密钥管理，不要生成包含真实值的仓库文件。部署时必须显式设置 `SPRING_PROFILES_ACTIVE=prod`。

健康检查：

- 存活：`GET /actuator/health/liveness`
- 就绪：`GET /actuator/health/readiness`

云平台应把流量只发送到就绪实例，并保留上一版镜像以便应用回滚。容器监听 `PORT`，未注入时默认为 8080。

## 3. HTTPS 与域名

推荐使用两个域名：

- `https://api.example.com`：Spring Boot API，在小程序后台登记为 request 合法域名；
- `https://admin.example.com`：管理员静态网页，同时作为后端唯一允许的生产 CORS Origin。

微信云托管可以在平台入口终止 TLS，容器内部保持 HTTP；若使用普通云服务器，则可用 `deploy/nginx/api.conf.template` 在 Nginx 终止 TLS。只向公网开放 443，MySQL 和未来 Redis 只开放私网访问。

小程序正式构建：

```powershell
$env:TARO_APP_API_BASE_URL='https://api.example.com/api/v1'
npm.cmd run build:weapp
```

管理员网页正式构建：

```powershell
$env:VITE_API_BASE_URL='https://api.example.com/api/v1'
npm.cmd run build
```

两个生产构建现在都会在 API 地址缺失或不是 HTTPS 时直接失败，避免把 `127.0.0.1` 打进正式包。

## 4. 生产环境变量

完整变量名见 `deploy/production.env.example`。至少包括：

- MySQL URL、最小权限账号和密码；URL 应启用并校验数据库 TLS；
- 当前 JWT key id 与至少 32 字节随机密钥；轮换时短期保留上一把密钥；
- 微信 AppID/AppSecret；
- 管理网页的精确 HTTPS Origin；
- 容器端口和数据库连接池上限。
- Redis 地址和认证密码；同机 Docker Redis 使用 `AGENT_WORK_FOOT_REDIS_SSL=false`，托管 Redis 使用 TLS。

连接池上限必须结合数据库最大连接数和应用实例数计算。例如每实例上限 20、最多 4 个实例，会最多占用约 80 条业务连接，不能只看单实例配置。

## 5. 备份、恢复与回滚

优先开启托管 MySQL 的自动备份、binlog/时间点恢复和跨故障域存储；仓库脚本用于额外的逻辑备份与恢复演练，不能替代云数据库能力。

建议起始目标：每日一次完整备份、保留 30 天，每季度至少做一次独立恢复验证。若业务可接受的数据丢失小于 24 小时，应再启用 binlog/时间点恢复并按实际目标调整频率。

逻辑备份：

```bash
MYSQL_HOST=mysql.internal MYSQL_DATABASE=agent_work_foot MYSQL_USER=backup_user \
MYSQL_PWD='由密钥管理临时注入' BACKUP_DIR=/secure-backups \
bash deploy/backup/backup-mysql.sh
```

恢复验证必须使用独立数据库，名称必须以 `_restore_verify` 结尾：

```bash
MYSQL_HOST=mysql.internal MYSQL_USER=restore_operator MYSQL_PWD='由密钥管理临时注入' \
BACKUP_FILE=/secure-backups/agent_work_foot-时间.sql.gz \
RESTORE_DATABASE=agent_work_foot_restore_verify \
bash deploy/backup/verify-restore.sh
```

验证内容至少包括校验和、Flyway 历史、核心表数量、应用使用恢复库启动后的只读冒烟测试。脚本不会自动删除验证库，避免误删；验收后通过受控数据库流程处理。

应用回滚与数据库恢复不是一回事：

1. 代码问题优先把流量切回上一版镜像；
2. Flyway 已执行的迁移不直接向下回滚，先确认旧代码是否兼容当前结构；
3. 只有数据损坏等事故才执行数据库恢复，并明确恢复时间点之后的数据会丢失；
4. 恢复前保留事故现场快照，恢复后执行核心接口和真实微信登录冒烟测试。

备份包含账号、OpenID、密码哈希和敏感偏好，必须加密、限制下载、记录访问并按保留期销毁。

## 6. 上线检查

- 使用 `prod` Profile 启动，缺少任一必填变量时启动失败；
- Flyway 迁移成功，数据库没有公网入口；
- `/actuator/health` 只返回必要状态，不暴露组件详情；
- 微信后台合法域名与正式包 API 地址一致；
- 管理后台 CORS 只允许实际生产域名；
- Redis 不开放公网，Docker Compose 健康检查为通过，且应用实例使用相同的 Redis 与 HMAC 密钥；
- 执行一次备份和独立恢复验证；
- 用上一版镜像演练应用回滚；
- 完成真机微信登录、同意、食物、抽取、记录和注销冒烟测试。
