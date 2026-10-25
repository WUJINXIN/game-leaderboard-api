# Game Leaderboard API

**Language:** English | [简体中文](README.zh-CN.md) | [日本語](README.ja.md)

[![Java CI](https://github.com/WUJINXIN/game-leaderboard-api/actions/workflows/maven-test.yml/badge.svg)](https://github.com/WUJINXIN/game-leaderboard-api/actions/workflows/maven-test.yml)

A REST API for managing players and retrieving game leaderboards, built with **Java 21**, **Spring Boot 4.1.1**, **PostgreSQL**, and **Redis**.

This project demonstrates REST API development, relational persistence, schema migrations, Redis cache-aside caching, transaction-aware cache invalidation, automated testing, and static analysis in GitHub Actions.

## Tech Stack

- **Backend:** Java 21, Spring Boot 4.1.1, Spring Web MVC
- **Persistence:** Spring Data JPA, Hibernate, PostgreSQL 17
- **Database migrations:** Flyway
- **Cache:** Redis Sorted Sets, Hashes, and TTL
- **Testing:** JUnit, Spring Boot Test, Mockito
- **Code quality:** SpotBugs Maven Plugin
- **CI:** GitHub Actions with PostgreSQL and Redis service containers
- **Build:** Maven Wrapper (`mvnw`)

## Features

- List players and look up a player by ID
- Create a new player; reject duplicate player IDs
- Update a player's score using HTTP `PATCH`
- Retrieve a leaderboard ordered by **score descending, then player ID ascending**
- Request a limited leaderboard (`limit` from 1 to 100)
- Cache the full leaderboard in Redis using the **cache-aside** pattern
- Store scores in a Redis **Sorted Set** and player names in a Redis **Hash**
- Use a **ready marker** to distinguish an empty cached leaderboard from a cache miss
- Set a 60-second TTL and rebuild cache contents using Redis `MULTI`/`EXEC`
- Invalidate the leaderboard cache after a successful PostgreSQL transaction commit for player creation or score updates
- Fall back to PostgreSQL if Redis leaderboard reading or rebuilding fails

## Architecture

```text
                  HTTP clients
                       |
                       v
               PlayersController
                       |
                       v
                 PlayerService
                  /         \
                 v           v
          PostgreSQL       Redis
          (source of       (leaderboard
            truth)            cache)
             |                |
         Flyway/JPA      Sorted Set: scores
                         Hash: names
                         String: ready marker
```

**Cache-aside for `GET /api/leaderboard`:**

1. Check the Redis ready marker.
2. On a cache hit, read the leaderboard from Redis.
3. On a miss, query PostgreSQL, rebuild the Redis cache, and return the database result.
4. After a successful database transaction that creates a player or updates a score, invalidate the Redis leaderboard cache.

> **Current limitations:** The `limit` endpoint currently queries PostgreSQL directly rather than using Redis. Cache invalidation after commit is implemented, but Redis failures during the after-commit callback and concurrent cache rebuild races still need more robust handling. This is a learning project, not a production-hardened distributed caching solution.

## Prerequisites

- JDK 21
- PostgreSQL (tested with version 17)
- Redis (tested locally with Redis 8; CI uses Redis 7)
- macOS, Linux, or Windows with a compatible shell

You can build and run the application with the included Maven Wrapper; a separate Maven installation is not required.

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/WUJINXIN/game-leaderboard-api.git
cd game-leaderboard-api
```

### 2. Prepare PostgreSQL

Connect as a PostgreSQL administrator and create a user and database, for example:

```sql
CREATE USER game_app WITH PASSWORD 'change_me_locally';
CREATE DATABASE game_leaderboard OWNER game_app;
```

Flyway automatically applies the database migration on application startup. The initial schema includes a `players` table with `id`, `name`, and `score` columns.

### 3. Start Redis

Redis should be reachable on `localhost:6379`.

```bash
redis-cli ping
# PONG
```

For macOS with Homebrew, you can start the Redis service using:

```bash
brew services start redis
```

### 4. Set application environment variables

The application reads its database password from the `DB_PASSWORD` environment variable. The connection URL defaults to `jdbc:postgresql://localhost:5432/game_leaderboard`.

```bash
export DB_PASSWORD='change_me_locally'
# Optional:
export DB_URL='jdbc:postgresql://localhost:5432/game_leaderboard'
```

Do **not** commit real passwords, API keys, or `.env` files containing secrets.

### 5. Run the application

```bash
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080` by default.

## REST API

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/players` | List all players |
| `GET` | `/api/players/{id}` | Get a player by ID |
| `POST` | `/api/players` | Create a player |
| `PATCH` | `/api/players/{id}/score` | Update a player's score |
| `GET` | `/api/leaderboard` | Get the full leaderboard (Redis cache-aside) |
| `GET` | `/api/leaderboard?limit=10` | Get the top N players (N = 1–100; PostgreSQL query) |

### Example requests

**Create a player:**

```bash
curl -i -X POST http://localhost:8080/api/players \
  -H 'Content-Type: application/json' \
  -d '{"id":1,"name":"Alice","score":1500}'
```

**Update a score:**

```bash
curl -i -X PATCH http://localhost:8080/api/players/1/score \
  -H 'Content-Type: application/json' \
  -d '{"score":2500}'
```

**Get the leaderboard:**

```bash
curl -s http://localhost:8080/api/leaderboard
```

Example response (depends on your database contents):

```json
[
  {"id": 1, "name": "Alice", "score": 2500}
]
```

**Get the top 10 players:**

```bash
curl -s 'http://localhost:8080/api/leaderboard?limit=10'
```

### Selected validation behavior

- Duplicate player ID: `409 Conflict`
- Unknown player ID: `404 Not Found`
- Negative updated score or missing `score` field: `400 Bad Request`
- Leaderboard `limit` outside 1–100: `400 Bad Request`

## Redis Cache Keys

| Key | Redis type | Purpose |
| --- | --- | --- |
| `leaderboard:cache:v1` | Sorted Set | Player IDs and scores |
| `leaderboard:cache:v1:names` | Hash | Player IDs and names |
| `leaderboard:cache:v1:ready` | String | Cache readiness marker |

The leaderboard cache uses a 60-second TTL. The ready marker also supports a cached **empty** leaderboard, for which the Sorted Set and Hash may not exist.

Inspect the local Redis cache with:

```bash
redis-cli EXISTS leaderboard:cache:v1:ready
redis-cli TTL leaderboard:cache:v1:ready
redis-cli ZRANGE leaderboard:cache:v1 0 -1 WITHSCORES
redis-cli HGETALL leaderboard:cache:v1:names
```

## Tests and Static Analysis

Run the test suite (requires PostgreSQL and Redis to be running and the database credentials to be configured):

```bash
./mvnw test
```

Run SpotBugs after compilation:

```bash
./mvnw test-compile spotbugs:check
```

Or run both in sequence:

```bash
./mvnw test spotbugs:check
```

Tests include API/controller checks, service unit tests, PostgreSQL repository integration tests, Redis connection tests, and Redis leaderboard cache tests.

## Continuous Integration

The workflow in [`.github/workflows/maven-test.yml`](.github/workflows/maven-test.yml) runs on pushes and pull requests to `main` and performs:

1. Start PostgreSQL 17 and Redis 7 service containers.
2. Configure Temurin JDK 21.
3. Verify the Redis connection.
4. Run Maven/JUnit tests.
5. Run SpotBugs static analysis.

See the [GitHub Actions runs](https://github.com/WUJINXIN/game-leaderboard-api/actions).

## Project Structure

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
  db/migration/                    # Flyway migrations
src/test/java/com/example/gameleaderboard/
  ...                                # Unit and integration tests
pom.xml
mvnw
```

## Planned Improvements

- Graceful handling of Redis failures in post-commit cache invalidation
- Concurrency-safe cache rebuilding and invalidation
- Optional Redis-backed pagination for leaderboard limits
- Additional integration tests for real PostgreSQL commit/rollback behavior
- More structured application logging

## License

No license file is currently included. All rights remain with the repository owner unless a license is added.
