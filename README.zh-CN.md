# 游戏排行榜 API

**语言：** [English](README.md) | 简体中文 | [日本語](README.ja.md)

[![Java CI](https://github.com/WUJINXIN/game-leaderboard-api/actions/workflows/maven-test.yml/badge.svg)](https://github.com/WUJINXIN/game-leaderboard-api/actions/workflows/maven-test.yml)

使用 **Java 21、Spring Boot 4.1.1、PostgreSQL 和 Redis** 开发的玩家管理与游戏排行榜 REST API。

本项目展示 REST API 开发、关系数据库持久化、数据库版本迁移、Redis Cache-Aside 缓存、事务提交后缓存失效、自动化测试和 GitHub Actions 静态分析。

## 技术栈

- **后端：** Java 21、Spring Boot 4.1.1、Spring Web MVC
- **持久化：** Spring Data JPA、Hibernate、PostgreSQL 17
- **数据库迁移：** Flyway
- **缓存：** Redis Sorted Set、Hash、TTL
- **测试：** JUnit、Spring Boot Test、Mockito
- **代码质量：** SpotBugs Maven Plugin
- **持续集成：** GitHub Actions，使用 PostgreSQL 和 Redis 服务容器
- **构建：** Maven Wrapper（`mvnw`）

## 核心功能

- 查询全部玩家或通过 ID 查询单个玩家
- 新增玩家，拒绝重复的玩家 ID
- 通过 HTTP `PATCH` 更新玩家分数
- 按**分数降序、玩家 ID 升序**返回排行榜
- 使用 `limit` 查询前 N 名（有效范围 1～100）
- 使用 **Cache-Aside（旁路缓存）**模式缓存完整排行榜
- 使用 Redis **Sorted Set** 存储玩家 ID 与分数、**Hash** 存储姓名
- 使用 **Ready Marker（就绪标记）**区分缓存未命中和已缓存的空排行榜
- 使用 60 秒 TTL，借助 Redis `MULTI` / `EXEC` 重建缓存
- 在新增玩家或更新分数的 PostgreSQL 事务成功提交后，使排行榜缓存失效
- Redis 排行榜读取或重建失败时，回退到 PostgreSQL

## 系统架构

```text
                  HTTP 客户端
                       |
                       v
               PlayersController
                       |
                       v
                 PlayerService
                  /         \
                 v           v
          PostgreSQL       Redis
          （权威数据源）   （排行榜缓存）
             |                |
         Flyway/JPA     Sorted Set：分数
                        Hash：姓名
                        String：就绪标记
```

**`GET /api/leaderboard` 的 Cache-Aside 流程：**

1. 检查 Redis Ready Marker。
2. 缓存命中时，从 Redis 读取完整排行榜。
3. 缓存未命中时，查询 PostgreSQL，重建 Redis 缓存，然后返回数据库查询结果。
4. 新增玩家或更新分数的数据库事务提交成功后，删除排行榜缓存。

> **当前限制：** 带 `limit` 的排行榜接口目前直接查询 PostgreSQL，尚未使用 Redis。虽然已实现提交后的缓存失效，但提交后 Redis 故障处理以及并发重建的竞争问题仍需完善。本项目属于学习实践项目，并非已经完成生产级加固的分布式缓存系统。

## 环境要求

- JDK 21
- PostgreSQL（已在 17 版本测试）
- Redis（本地使用 Redis 8，CI 使用 Redis 7）
- macOS、Linux 或具备兼容 Shell 的 Windows

项目已包含 Maven Wrapper，无需另外安装 Maven。

## 快速开始

### 1. 克隆仓库

```bash
git clone https://github.com/WUJINXIN/game-leaderboard-api.git
cd game-leaderboard-api
```

### 2. 准备 PostgreSQL

使用管理员账号连接 PostgreSQL，创建用户和数据库，例如：

```sql
CREATE USER game_app WITH PASSWORD 'change_me_locally';
CREATE DATABASE game_leaderboard OWNER game_app;
```

启动应用时 Flyway 会自动执行数据库迁移。初始 `players` 表包含 `id`、`name` 和 `score` 字段。

### 3. 启动 Redis

Redis 应在 `localhost:6379` 可访问：

```bash
redis-cli ping
# PONG
```

在使用 Homebrew 的 macOS 上，可以执行：

```bash
brew services start redis
```

### 4. 配置环境变量

应用通过 `DB_PASSWORD` 读取数据库密码。默认 JDBC URL 为 `jdbc:postgresql://localhost:5432/game_leaderboard`。

```bash
export DB_PASSWORD='change_me_locally'
# 可选：
export DB_URL='jdbc:postgresql://localhost:5432/game_leaderboard'
```

**不要**把真实密码、API Key 或包含敏感信息的 `.env` 文件提交到 Git。

### 5. 启动应用

```bash
./mvnw spring-boot:run
```

默认地址为 `http://localhost:8080`。

## REST API

| 方法 | 接口 | 说明 |
| --- | --- | --- |
| `GET` | `/api/players` | 查询全部玩家 |
| `GET` | `/api/players/{id}` | 根据 ID 查询玩家 |
| `POST` | `/api/players` | 新增玩家 |
| `PATCH` | `/api/players/{id}/score` | 更新玩家分数 |
| `GET` | `/api/leaderboard` | 完整排行榜（Redis Cache-Aside） |
| `GET` | `/api/leaderboard?limit=10` | 前 N 名（N 为 1～100，直接查询 PostgreSQL） |

### 请求示例

**新增玩家：**

```bash
curl -i -X POST http://localhost:8080/api/players \
  -H 'Content-Type: application/json' \
  -d '{"id":1,"name":"Alice","score":1500}'
```

**更新分数：**

```bash
curl -i -X PATCH http://localhost:8080/api/players/1/score \
  -H 'Content-Type: application/json' \
  -d '{"score":2500}'
```

**获取排行榜：**

```bash
curl -s http://localhost:8080/api/leaderboard
```

响应示例（实际内容取决于数据库）：

```json
[
  {"id": 1, "name": "Alice", "score": 2500}
]
```

**查询前10名：**

```bash
curl -s 'http://localhost:8080/api/leaderboard?limit=10'
```

### 部分参数校验与错误响应

- 玩家 ID 重复：`409 Conflict`
- 玩家 ID 不存在：`404 Not Found`
- 更新分数为负数或缺少 `score` 字段：`400 Bad Request`
- `limit` 不在 1～100 之间：`400 Bad Request`

## Redis 缓存 Key

| Key | Redis 类型 | 用途 |
| --- | --- | --- |
| `leaderboard:cache:v1` | Sorted Set | 玩家 ID 与分数 |
| `leaderboard:cache:v1:names` | Hash | 玩家 ID 与姓名 |
| `leaderboard:cache:v1:ready` | String | 缓存就绪标记 |

排行榜缓存使用 60 秒 TTL。即使排行榜为空，Ready Marker 也可以存在，此时 Sorted Set 和 Hash 可以不存在。

本地查看 Redis 缓存：

```bash
redis-cli EXISTS leaderboard:cache:v1:ready
redis-cli TTL leaderboard:cache:v1:ready
redis-cli ZRANGE leaderboard:cache:v1 0 -1 WITHSCORES
redis-cli HGETALL leaderboard:cache:v1:names
```

## 测试与静态分析

运行测试前，需要启动 PostgreSQL 和 Redis，并配置数据库连接信息。

```bash
./mvnw test
```

编译后执行 SpotBugs：

```bash
./mvnw test-compile spotbugs:check
```

也可以连续执行测试与静态分析：

```bash
./mvnw test spotbugs:check
```

测试覆盖 API / Controller、Service 单元测试、PostgreSQL Repository 集成测试、Redis 连接和排行榜缓存。

## 持续集成（CI）

[`.github/workflows/maven-test.yml`](.github/workflows/maven-test.yml) 在 Push 或向 `main` 提交 Pull Request 时执行：

1. 启动 PostgreSQL 17 与 Redis 7 服务容器。
2. 配置 Temurin JDK 21。
3. 检查 Redis 连接。
4. 执行 Maven / JUnit 测试。
5. 运行 SpotBugs 静态代码分析。

查看 [GitHub Actions 运行记录](https://github.com/WUJINXIN/game-leaderboard-api/actions)。

## 项目结构

```text
.github/workflows/maven-test.yml     # GitHub Actions CI
src/main/java/com/example/gameleaderboard/
  GameLeaderboardApiApplication.java
  Player.java
  PlayerEntity.java
  PlayerRepository.java
  PlayerService.java
  PlayersController.java
  RedisLeaderboardCache.java
src/main/resources/
  application.properties
  db/migration/                    # Flyway 迁移脚本
src/test/java/com/example/gameleaderboard/
  ...                                # 单元测试与集成测试
pom.xml
mvnw
```

## 后续改进计划

- 更稳健地处理数据库提交后 Redis 缓存失效操作的异常
- 实现并发安全的缓存重建和失效机制
- 可选：让带 `limit` 的排行榜查询使用 Redis
- 增加真实 PostgreSQL 提交 / 回滚的集成测试
- 改进结构化日志记录

## 许可证

仓库目前没有单独的许可证文件。如未另行授权，相关权利由仓库所有者保留。
