# Redis 接入与运行说明

Redis 已完成连接和 Docker 运行准备；当前不启用限流。下一阶段将以缓存降低饭点时 MySQL 的读压力，不保存业务事实，也不承担跨请求的业务队列。MySQL 仍是食品、饮食记录、抽取结果、账号和同意事件的唯一事实来源。

## 暂缓：共享限流

已预留四类 Redis 限流实现：认证、账号安全、老虎机和管理员敏感操作。它们只会在 `app.redis.enabled=true` 时替换 `InMemory*RateLimiter`；当前所有 Profile 都保持 `false`，继续使用本机内存限流。

- 使用 Lua 脚本或等价原子命令完成计数与 TTL；
- key 不直接保存邮箱、用户原始标识或完整 IP，使用部署密钥做 HMAC 后再组成 key；
- TTL 必须覆盖限流窗口，并为 key 加业务前缀和版本号；
- Redis 连接和命令超时从第一天就设置为短超时；
- 网关保留总 QPS/并发上限，Redis 负责用户与账号维度限流。

实际 key：

```text
awf:v1:rl:wechat-login:{ipHmac}
awf:v1:rl:login:{ipHmac}:{emailHmac}
awf:v1:rl:register:{ipHmac}
awf:v1:rl:refresh:{ipHmac}
awf:v1:rl:account-security:{userHmac}:{ipHmac}
awf:v1:rl:slot:{userHmac}
awf:v1:rl:admin-target:{adminHmac}:{targetHmac}:{ipHmac}
awf:v1:rl:admin:{adminHmac}:{ipHmac}
```

限流脚本用 Redis Lua 原子执行 `INCR + PEXPIRE + 阈值判断`。Redis 短暂故障时会改用本机同阈值固定窗口兜底，因此不会无限阻塞请求；网关仍应限制总 QPS 和并发。该兜底不是跨实例共享的，Redis 告警必须触发人工处理。

## Docker 运行 Redis

Docker 方案适用于单台云主机或测试环境；多可用区生产环境优先使用私网托管 Redis。Compose 默认只监听宿主机 `127.0.0.1:6379`，不向公网开放端口，并启用 AOF 持久化、密码认证、健康检查和 Docker 卷。

```bash
cp deploy/redis.docker.env.example deploy/redis.docker.env
# 将 REDIS_PASSWORD 改为由密钥管理器生成的随机长密钥；该文件不可提交。
docker compose --env-file deploy/redis.docker.env -f deploy/docker-compose.redis.yml up -d
docker compose --env-file deploy/redis.docker.env -f deploy/docker-compose.redis.yml ps
```

后端同机部署时，设置：

```text
SPRING_PROFILES_ACTIVE=prod
AGENT_WORK_FOOT_REDIS_HOST=127.0.0.1
AGENT_WORK_FOOT_REDIS_PORT=6379
AGENT_WORK_FOOT_REDIS_USERNAME=default
AGENT_WORK_FOOT_REDIS_PASSWORD=<与 REDIS_PASSWORD 相同>
AGENT_WORK_FOOT_REDIS_SSL=false
```

Redis 容器使用卷 `redis-data`；升级或迁移前先完成一次可恢复的卷快照。缓存投入使用后，即使 Redis 数据丢失，应用也应回源 MySQL，不会损坏业务数据。

## 下一阶段：低风险缓存

先从下列低风险读缓存开始，并取得慢查询数据与缓存命中率：

1. 偏好预设、默认食物模板；
2. 管理 Dashboard 的短 TTL 聚合结果；
3. 用户食物池与同意/权限状态的短 TTL 缓存，写成功后主动删除。

老虎机确认、饮食记录、账号状态和同意事件仍以 MySQL 为唯一事实来源。Redis 不负责最终幂等和持久化。

## 运行前置条件

- 创建私网托管 Redis，启用认证与 TLS，不开放公网；若使用本仓库 Docker Compose，则使用回环监听并由同机 API 访问；
- 将 `deploy/redis.env.example` 中的值放入云端密钥/变量管理；
- 已增加 Spring Data Redis 依赖和独立配置属性；
- 已用条件 Bean 区分本地内存实现与生产 Redis 实现；
- 后续补充 Redis Testcontainers 集成测试；
- 定义连接失败、命令超时、缓存命中率和内存使用告警。

## 验收条件

- 两个应用实例对同一个 key 看到统一计数；
- 并发下不会超发配额，窗口到期后可恢复；
- Redis 超时不会无限阻塞 Web 请求；
- 敏感原始标识不会出现在 key、日志或指标标签；
- Redis 故障演练时，网关和本地兜底策略按预期工作；
- 删除/修改食物、偏好、账号或同意状态后不会持续读取旧缓存。
