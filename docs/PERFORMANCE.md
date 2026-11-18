# 压测报告（JMeter 同类的 autocannon，本地单机）

> 目的：给简历与 README 提供**可复现**的性能数据，并记录一次真实的瓶颈发现与调优过程。

## 环境

| 项 | 值 |
| --- | --- |
| 机器 | Windows 11，本地开发机（非服务器） |
| 应用 | `library-system.jar`（Spring Boot 3.3.5 内嵌 Tomcat），JVM 默认堆 |
| 数据库 | MySQL 8.0 本机，InnoDB，演示数据（12 种图书 / 6 位读者 / ~20 条借阅） |
| 压测工具 | autocannon 7.15.0，`-c 200 -d 12`（200 并发连接，持续 12 秒） |
| 预热 | 每场景先跑 5 秒低并发预热（避免 JIT 冷启动污染数据） |

## 结果（预热后测量）

| 场景 | 接口 | 吞吐 | p50 | p90 | p99 | 成功率 |
| --- | --- | --- | --- | --- | --- | --- |
| 管理端分页列表 | `GET /books?page=1&pageSize=10` | **4,201 req/s** | 92ms | 142ms | **147ms** | 99.2% |
| 联表分页查询 | `GET /loans?status=ALL&pageSize=10` | **3,282 req/s** | 98ms | 125ms | **142ms** | 99.5% |
| 读者门户检索 | `GET /reader/books?pageSize=10` | **3,850 req/s** | 98ms | 151ms | **168ms** | 99.1% |

> 失败请求均为 autocannon 本机 200 连接下的 TCP 连接重置（`ECONNRESET`），不是接口 5xx；
> 所有响应状态码均为 200。

## 一次真实的优化过程

| 轮次 | 配置 | 吞吐 | p99 | 观察 |
| --- | --- | --- | --- | --- |
| 1 | Hikari 默认（池 10） | 1,499 req/s | 945ms | 冷启动 + 连接池排队，尾延迟抖动大 |
| 2 | 连接池 50 | 1,131 req/s | 1,358ms | 连接错误 48 → 3，但本机 4 核被线程抢占，吞吐反而下降 |
| 3 | 连接池 50 + 预热 5s | **4,201 req/s** | **147ms** | 尾延迟下降 84%，说明首轮抖动主要来自 JIT 与连接建立 |

**结论**：读多写少的接口，瓶颈在**连接池排队与 JVM 预热**，而不是 SQL；压测前必须预热，否则数据不可比。

## 复现命令

```bash
# 1) 启动应用
java -jar target/library-system.jar

# 2) 取 token
TOKEN=$(curl -s -X POST http://127.0.0.1:8090/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .data.token)

# 3) 预热 + 压测
npx autocannon -c 100 -d 5  -H "authorization=Bearer $TOKEN" "http://127.0.0.1:8090/books"
npx autocannon -c 200 -d 12 -H "authorization=Bearer $TOKEN" "http://127.0.0.1:8090/books?page=1&pageSize=10"
```

## 局限（面试如实说明）

- 单机、无反向代理、数据量小（万级以下），不代表生产容量；
- 未做写接口压测（借还涉及事务与行锁，需要独立造数据与清理）；
- 未测 GC 与内存曲线，若要给出容量结论需配合 `jstat` / `async-profiler`。
