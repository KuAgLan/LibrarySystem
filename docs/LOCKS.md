# 分布式锁：MySQL / Redis 双实现

定时任务（每日结算）在多实例部署时只应执行一次，本项目用 `DistributedLock` 接口抽象，两套实现按配置切换。

| 实现 | 依赖 | 适用场景 |
| --- | --- | --- |
| `MysqlLock`（默认） | 只要数据库 | 单库部署、不想额外运维中间件 |
| `RedisLock` | Redis 5+ | 已有 Redis、任务频繁或需要更低延迟 |

## 配置

```yaml
app:
  lock:
    provider: ${LOCK_PROVIDER:mysql}   # mysql | redis
    ttl-seconds: 60                    # 租约时长，持有者崩溃后自动释放
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      # password: your-password
```

```bash
LOCK_PROVIDER=redis java -jar target/library-system.jar
# 或 Windows PowerShell
$env:LOCK_PROVIDER="redis"; java -jar target/library-system.jar
```

## 实现要点

**MySQL**（`lock_guard` 表）

```sql
CREATE TABLE lock_guard (
  lock_name VARCHAR(64) PRIMARY KEY,
  owner     VARCHAR(64) NOT NULL,
  expire_at DATETIME(3) NOT NULL
);
```

1. `INSERT` 成功即拿到锁（主键唯一兜底）
2. 冲突时 `UPDATE ... WHERE expire_at < NOW(3)` 抢占已过期锁（宕机实例遗留）
3. `DELETE ... WHERE owner = ?` 只允许持有者释放
4. 看门狗 `UPDATE ... SET expire_at` 续租

**Redis**（`SET NX PX` + Lua）

```lua
-- 释放：owner 匹配才删
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
```

1. `SET library:lock:{key} {owner} NX PX {ttl}` 获取
2. 释放/续期走 Lua，保证「判断 owner」与「修改」原子执行，避免误删他人锁
3. 键带过期时间，宕机后自动释放

## 安装 Redis

**Windows（便携版，无需管理员）**

```powershell
# 下载 tporadowski 维护的 5.0.14.1
Invoke-WebRequest https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.zip -OutFile redis.zip
Expand-Archive redis.zip -DestinationPath .tools\redis
.tools\redis\redis-server.exe .tools\redis\redis.conf   # 前台启动
.tools\redis\redis-cli.exe ping                          # 期望 PONG
```

**Linux**

```bash
sudo apt install -y redis-server
sudo systemctl enable --now redis-server
redis-cli ping
```

**Docker**

```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

## 验证清单

```powershell
# 1. 切换为 Redis 锁后启动，确认 provider
curl http://127.0.0.1:8090/admin/jobs/locks     # provider: redis

# 2. 并发互斥（4 个请求同时打，只有 1 个执行）
node docs/tests/lock-test.mjs http://127.0.0.1:8090

# 3. 运行期观察锁键
redis-cli get  library:lock:job:nightly-settle  # 32 位 owner
redis-cli pttl library:lock:job:nightly-settle  # 剩余租约，≤60000
redis-cli keys library:lock:*                   # 执行结束后为空
```

实测结果（Windows + Redis 5.0.14.1）：

| 检查项 | 结果 |
| --- | --- |
| 切换 `LOCK_PROVIDER=redis` 后 provider 显示 redis | ✅ |
| `docs/tests/lock-test.mjs`（10 项） | ✅ 10/10 |
| 持锁期间键存在且 owner 为 32 位 UUID | ✅ |
| 租约 TTL 生效（≈60s，看门狗续租） | ✅ |
| 持锁期间第二个请求 409 | ✅ |
| 任务完成后键自动删除 | ✅ |
| 外部遗留过期锁可被抢占（模拟宕机） | ✅ |
| Lua 释放：非持有者返回 0 / 持有者返回 1 | ✅ |

## 注意事项

- 换实现只改配置，业务代码（`JobService`）不变；`JobController#locks` 在 Redis 模式下返回空列表（无法列举键，需要时用 `SCAN library:lock:*`）
- Redis 模式下应用启动不依赖 Redis 可用（Lettuce 懒连接），但任务执行时会失败，请配合健康检查
- 多实例务必保持 NTP 时间同步：MySQL 实现依赖 `NOW(3)` 判断过期