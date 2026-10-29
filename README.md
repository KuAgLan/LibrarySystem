<div align="center">

# 图书管理系统 · LibrarySystem

Java 控制台版图书管理系统，覆盖**图书 / 读者 / 借还 / 预约 / 统计报表**五块业务。
**Java 17 + JDBC + MySQL（InnoDB）**，自带 **17 项业务规则自测**，其中包含 10 线程并发抢书的验证。

![Java](https://img.shields.io/badge/Java-17-orange)
![MySQL](https://img.shields.io/badge/MySQL-8.0%2B%20%7C%20MariaDB%2010.6%2B-4479A1)
![Tests](https://img.shields.io/badge/selftest-17%20passed-2ea44f)
![License](https://img.shields.io/badge/license-MIT-d8b04a)

</div>

## 功能

| 模块 | 能力 |
| --- | --- |
| 图书管理 | 新增 / 修改 / 软删除、关键字搜索（书名 / 作者 / ISBN）、库存与可借数分离 |
| 读者管理 | 借书证注册、借阅上限、欠费余额、启用停用 |
| 借还 | 上限校验、逾期禁借、欠费封禁、事务内扣减库存、归还自动结算罚金 |
| 预约 | 无可借副本时排队，归还时按先来后到通知队列第一位 |
| 报表 | 分类借阅量、月度趋势、热门 TOP 10、逾期名单、导出 CSV |
| 审计 | 所有写操作写 `audit_log`，可追溯 |

## 环境要求

- **JDK 17**（`javac`、`jar` 在 PATH 里即可）
- **MySQL 8.0+**（已在 **MySQL 8.0.46** 上实测；MariaDB 10.6+ 同样可用）
- 驱动：`lib/mysql-connector-j-8.0.33.jar`（已附带，无需联网）

## 在 IDEA 里运行

**方式 A：当 Maven 项目打开（推荐）**

`File → Open` 选中本目录，IDEA 会自动识别 `pom.xml` 并下载 `mysql-connector-j` 依赖，
然后直接运行 `library.Main` 即可。

**方式 B：普通 Java 项目（不加 Maven）**

直接运行 `Main.java` 会报 `No suitable driver found`，因为驱动没进依赖。手动加一下：

```
File → Project Structure (Ctrl+Alt+Shift+S)
  → Modules → Dependencies → + → JARs or directories
  → 选 lib/mysql-connector-j-8.0.33.jar → OK → Apply
```

**方式 C：不折腾 IDE，直接用打包好的 jar**

```bat
setup-mysql.bat   :: 首次初始化（建库 + 建号 + 灌数据 + 自测）
run.bat           :: 进菜单
```

## 快速开始

### 一键初始化（推荐）

```bat
setup-mysql.bat
```

它会依次做四件事，中途只问你一次 MySQL 的 root 密码（不落盘）：

1. 建库 `library`（utf8mb4）
2. 建应用账号 `library / library123`，授予 `library.*` 权限
3. 建表 + 灌示例数据（10 本书、4 位读者）
4. 跑 17 项自测，全过才提示「全部就绪」

### 手动方式

```sql
-- 建库建表
mysql -u root -p < sql/schema.sql

-- 或者让程序自己建（可重复执行）
run.bat --init-db
```

要单独建账号的话：

```sql
CREATE USER 'library'@'localhost' IDENTIFIED BY 'library123';
GRANT ALL PRIVILEGES ON library.* TO 'library'@'localhost';
FLUSH PRIVILEGES;
```

### 2. 配置连接

三种方式任选其一：

```bat
:: A. 命令行参数
run.bat --url "jdbc:mysql://127.0.0.1:3306/library?useSSL=false&serverTimezone=Asia/Shanghai" --user root --password 123456

:: B. 环境变量（推荐）
set LIB_URL=jdbc:mysql://127.0.0.1:3306/library?useSSL=false&serverTimezone=Asia/Shanghai
set LIB_USER=root
set LIB_PASSWORD=123456
run.bat

:: C. 直接改 Db.java 里的默认 url / user / password，再 build.bat
```

### 3. 构建与运行

```bat
build.bat            :: 编译 + 打包 library-system.jar
run.bat --seed       :: 灌 10 本书 + 4 位读者
run.bat              :: 进菜单
```

默认连接：`127.0.0.1:3306/library`，账号 `library` / `library123`（在 `Db.java` 里可改）。

菜单：

```
1. 图书管理      2. 读者管理
3. 借书 / 还书   4. 预约
5. 统计报表      0. 退出
```

### 其它命令

```bat
run.bat --demo            :: 演示：借书 → 逾期 → 预约 → 报表（会清库重建）
run.bat --selftest        :: 17 项业务规则自测
run.bat --report out.csv  :: 导出热门图书 CSV
run.bat --settle          :: 每日结算（分布式锁保护，多实例只跑一个）
    --lock=redis --redis=127.0.0.1:6379   换 Redis 实现
run.bat --reset           :: 删库重建（危险）
run.bat --help
```

## 业务规则

| 规则 | 取值 |
| --- | --- |
| 借期 | 30 天 |
| 借阅上限 | 默认 5 册（可按读者设置） |
| 逾期罚金 | 每册每天 **0.5 元**，归还时结算，计入读者欠费 |
| 欠费封禁 | 欠费 > **20 元** 禁止借阅 |
| 逾期禁借 | 有逾期未还的书时不允许再借 |
| 重复借阅 | 同一本书同一个人同时只能借一册 |
| 预约 | 仅当可借数为 0 时可预约，一人一书只排一次 |

## 数据库设计（MySQL / InnoDB）

| 表 | 说明 |
| --- | --- |
| `books` | 图书主表，`CHECK (available >= 0 AND available <= total)`、`UNIQUE(isbn)` |
| `readers` | 读者，`max_loans` / `balance` / `enabled`，`UNIQUE(card_no)` |
| `loans` | 借阅记录，`returned_at IS NULL` 表示未归还，外键指向 books / readers |
| `reservations` | 预约队列，`notified_at` 记录是否已通知 |
| `audit_log` | 写操作审计 |

索引：

```sql
KEY idx_loans_reader_date (reader_id, borrowed_at)   -- 按读者查历史
KEY idx_loans_book (book_id)                         -- 按书统计借阅次数
KEY idx_loans_open (reader_id, returned_at)          -- 查未归还（逾期判定）
KEY idx_res_book (book_id, cancelled, created_at)    -- 预约队列取队首
```

完整脚本见 [`sql/schema.sql`](sql/schema.sql)。

## 并发是怎么做对的

借书 =「校验 + 扣库存 + 写记录」，最容易出错的是**两个请求同时抢最后一册**：

1. 事务统一由 `LibraryService.tx()` 管理：`setAutoCommit(false)` → 提交 / 回滚；
2. 扣库存用**条件更新**：

   ```sql
   UPDATE books SET available = available - 1 WHERE id = ? AND available > 0
   ```

   `executeUpdate()` 返回 0 说明被别人抢先，直接返回「该书暂无可借副本」；
3. InnoDB 行锁保证这条 UPDATE 串行执行，`CHECK` 约束兜底库存不会为负。

自测里专门验证：**10 个线程同时借只剩 1 册的书，只允许 1 个成功**，并断言库存不为负。

```
── 并发借书（10 个线程抢最后 1 册） ──
  ✔ 只有 1 个线程借成功
  ✔ 库存没有变成负数
```


## 分布式锁

借书靠的是「条件更新 + InnoDB 行锁」，单库足够。但**定时任务**这类跨实例的互斥，
行锁管不了（多个应用服务器各连各的库，也各跑各的定时器）。为此项目里实现了一套分布式锁：

```
library.lock.DistributedLock      // 接口：tryLock / renew / unlock / holder
library.lock.MysqlLock            // 实现一：lock_guard 表（不引入中间件）
library.lock.RedisLock            // 实现二：Redis SET NX PX + Lua（手写 RESP 客户端，零依赖）
library.service.NightlySettle     // 使用方：每日结算任务，多实例只跑一个
```

### 语义

| 要求 | 做法 |
| --- | --- |
| 互斥 | MySQL：`INSERT ... ON DUPLICATE KEY UPDATE`（只有过期行才可改写）<br>Redis：`SET key owner NX PX ttl` |
| 防死锁 | 锁带 TTL，持有者崩溃后自动过期 |
| 防误删 | 释放时校验 owner：MySQL `DELETE ... WHERE owner=?`；Redis 用 Lua `if get==owner then del` |
| 防锁过期 | 看门狗线程每 `ttl/3` 续期一次（`NightlySettle` 里演示），续期失败即中止任务 |

> 释放为什么必须用 Lua？如果先 `GET` 再 `DEL`，判断完到删除之间锁可能刚好过期被别人抢到，
> 结果把**别人的锁**删了——这是分布式锁最经典的坑。

### 演示：两个 JVM 同时触发结算

```bat
:: 窗口 A
java -cp "out;lib/mysql-connector-j-8.0.33.jar" library.Main --settle --settle-ms 3000 --lock=mysql
:: 窗口 B（1 秒后）
java -cp "out;lib/mysql-connector-j-8.0.33.jar" library.Main --settle --settle-ms 3000 --lock=mysql
```

```
[实例 A] [MySQL(lock_guard 表)] 结算任务 执行了
         完成：逾期 0 册，预估罚金 0.00 元   耗时 3096 ms

[实例 B] [MySQL(lock_guard 表)] 结算任务 没执行
         跳过：锁被其他实例占用（holder=2c5a24c1）
```

换成 `--lock=redis` 效果一致（需要本地 Redis，`--redis=127.0.0.1:6379`）。
完整记录见 [`docs/lock-demo.txt`](docs/lock-demo.txt)。

### 两种实现怎么选

| | MySQL 锁 | Redis 锁 |
| --- | --- | --- |
| 额外依赖 | 无 | 需要 Redis |
| 性能 | 每次抢锁一次写库，高频场景有压力 | 内存操作，微秒级 |
| 可靠性 | 依赖数据库可用性 | 依赖 Redis 可用性（可用 Redlock 多节点） |
| 适用 | 低频任务（定时结算、报表），本项目默认 | 高频、短临界区（限流、库存扣减） |

### 自测覆盖

`--selftest` 里对两种实现各跑 9 条断言：抢锁、互斥、非持有者释放无效、续期、
释放后可重入、TTL 过期可被抢占（`结果：35 通过，0 失败`）。

## 自测

```
$ run.bat --selftest

── 借阅规则 ──
  ✔ 借书成功
  ✔ 库存扣减到 0
  ✔ 无副本时借阅被拒绝
  ✔ 同一本书不能重复借
  ✔ 上限内第一册可借
  ✔ 达到借阅上限后被拒
  ✔ 归还成功
  ✔ 归还后库存回补到 1

── 逾期罚金 ──
  ✔ 逾期 10 天罚金 = 5.0
  ✔ 罚金记入读者欠费
  ✔ 欠费累计超过 20 元
  ✔ 欠费超限时拒绝借阅

── 预约队列 ──
  ✔ 无可借副本时可以预约
  ✔ 同一人不能重复预约
  ✔ 归还后通知队列第一位（乙）

── 并发借书（10 个线程抢最后 1 册） ──
  ✔ 只有 1 个线程借成功
  ✔ 库存没有变成负数

结果：17 通过，0 失败
```

完整演示输出见 [`docs/demo.txt`](docs/demo.txt)。

## 工程结构

```
LibrarySystem/
  build.bat / run.bat        一键构建 / 运行
  lib/mysql-connector-j-8.0.33.jar
  sql/schema.sql             建库建表（与 Schema.java 一致）
  src/main/java/library/
    Main.java                入口与参数解析
    Db.java                  连接、建库建表、参数绑定
    Schema.java              DDL（InnoDB + utf8mb4 + 外键 + CHECK）
    Models.java              Book / Reader / Loan / Reservation / LoanResult
    Dao.java                 纯 SQL：条件扣减、查未还、预约取队首
    LibraryService.java      业务规则 + 事务边界
    ReportService.java       统计报表 + CSV 导出
    Menu.java                交互式菜单
    Table.java               等宽表格（中文按 2 列宽对齐）
    SampleData.java          示例数据
    Demo.java                一键演示
    SelfTest.java            业务规则 + 分布式锁断言（35 项）
    lock/DistributedLock.java  分布式锁接口
    lock/MysqlLock.java        实现一：lock_guard 表
    lock/RedisLock.java        实现二：Redis + Lua（内置极简 RESP 客户端）
    lock/Locks.java            按 --lock=mysql|redis 选择实现
    service/NightlySettle.java 用锁保护的每日结算任务
```

分层是 `Menu → LibraryService → Dao → JDBC → MySQL`：Service 不碰 `System.in`，Dao 不碰业务规则，所以自测能直接调 Service 跑断言。

## 已知限制

- 控制台界面，没有 GUI（课程设计场景够用，重点在业务规则与数据一致性）；
- 罚金规则（0.5 元/天、20 元封顶）写在 `LibraryService` 常量里，实际业务应做成配置项；
- 未做分布式锁：单机 MySQL 靠行锁足够，多实例写同一套数据还需上层协调。

## 许可

MIT
