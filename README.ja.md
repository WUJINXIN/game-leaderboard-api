# ゲームランキング API

**言語：** [English](README.md) | [简体中文](README.zh-CN.md) | 日本語

[![Java CI](https://github.com/WUJINXIN/game-leaderboard-api/actions/workflows/maven-test.yml/badge.svg)](https://github.com/WUJINXIN/game-leaderboard-api/actions/workflows/maven-test.yml)

**Java 21、Spring Boot 4.1.1、PostgreSQL、Redis** を使用して開発した、プレイヤー管理とゲームランキング取得のための REST API です。

REST API の開発、リレーショナルデータベースへの永続化、スキーママイグレーション、Redis を用いた Cache-Aside、トランザクションのコミット後に行うキャッシュ無効化、GitHub Actions による自動テストおよび静的解析を実践するプロジェクトです。

## 技術スタック

- **バックエンド：** Java 21、Spring Boot 4.1.1、Spring Web MVC
- **データ永続化：** Spring Data JPA、Hibernate、PostgreSQL 17
- **DB マイグレーション：** Flyway
- **キャッシュ：** Redis Sorted Set、Hash、TTL
- **テスト：** JUnit、Spring Boot Test、Mockito
- **コード品質：** SpotBugs Maven Plugin
- **CI：** PostgreSQL / Redis のサービスコンテナを利用した GitHub Actions
- **ビルド：** Maven Wrapper（`mvnw`）

## 主な機能

- プレイヤー一覧および ID によるプレイヤー情報の取得
- プレイヤーの新規登録と重複 ID の拒否
- HTTP `PATCH` によるスコア更新
- **スコア降順、同点の場合はプレイヤー ID 昇順**のランキング取得
- `limit` による上位 N 件の取得（1～100）
- **Cache-Aside パターン**によるランキング全件の Redis キャッシュ
- Redis **Sorted Set** にプレイヤー ID とスコア、**Hash** にプレイヤー名を保存
- **Ready Marker** によりキャッシュミスとキャッシュ済みの空ランキングを区別
- 60 秒の TTL と Redis `MULTI` / `EXEC` を用いたキャッシュ再構築
- プレイヤー追加またはスコア更新の PostgreSQL トランザクションが正常にコミットされた後、ランキングキャッシュを無効化
- Redis の読み取りまたは再構築に失敗した場合は PostgreSQL にフォールバック

## システム構成

```text
                  HTTP クライアント
                       |
                       v
               PlayersController
                       |
                       v
                 PlayerService
                  /         \
                 v           v
          PostgreSQL       Redis
          （正本データ） （ランキングキャッシュ）
             |                |
         Flyway/JPA     Sorted Set：スコア
                        Hash：プレイヤー名
                        String：Ready Marker
```

**`GET /api/leaderboard` の Cache-Aside 処理：**

1. Redis の Ready Marker を確認します。
2. キャッシュヒット時は Redis からランキングを取得します。
3. キャッシュミス時は PostgreSQL を検索し、Redis キャッシュを再構築したうえで DB の検索結果を返します。
4. プレイヤー追加またはスコア更新が正常にコミットされた後、ランキングキャッシュを無効化します。

> **現状の制約：** `limit` パラメータ付きのエンドポイントは Redis ではなく PostgreSQL を直接検索します。コミット後のキャッシュ無効化は実装済みですが、コミット後の Redis 障害への対応やキャッシュ再構築時の競合制御には改善の余地があります。本プロジェクトは学習・実践を目的としており、本番環境向けの分散キャッシュを完全に実装したものではありません。

## 動作環境

- JDK 21
- PostgreSQL（バージョン 17 で動作確認）
- Redis（ローカルは Redis 8、CI は Redis 7）
- macOS、Linux、または互換性のあるシェルを備えた Windows

Maven Wrapper が同梱されているため、Maven を別途インストールする必要はありません。

## セットアップ

### 1. リポジトリのクローン

```bash
git clone https://github.com/WUJINXIN/game-leaderboard-api.git
cd game-leaderboard-api
```

### 2. PostgreSQL の準備

PostgreSQL の管理者ユーザーで接続し、以下のようにユーザーとデータベースを作成します。

```sql
CREATE USER game_app WITH PASSWORD 'change_me_locally';
CREATE DATABASE game_leaderboard OWNER game_app;
```

アプリケーション起動時に Flyway がマイグレーションを自動実行します。初期の `players` テーブルには `id`、`name`、`score` カラムがあります。

### 3. Redis の起動

Redis が `localhost:6379` で利用できることを確認します。

```bash
redis-cli ping
# PONG
```

macOS で Homebrew を使用している場合：

```bash
brew services start redis
```

### 4. 環境変数の設定

DB パスワードは環境変数 `DB_PASSWORD` から読み込みます。デフォルトの接続 URL は `jdbc:postgresql://localhost:5432/game_leaderboard` です。

```bash
export DB_PASSWORD='change_me_locally'
# 任意：
export DB_URL='jdbc:postgresql://localhost:5432/game_leaderboard'
```

実際のパスワード、API キー、機密情報を含む `.env` ファイルを Git にコミットしないでください。

### 5. アプリケーションの起動

```bash
./mvnw spring-boot:run
```

デフォルトの URL は `http://localhost:8080` です。

## REST API

| メソッド | エンドポイント | 説明 |
| --- | --- | --- |
| `GET` | `/api/players` | 全プレイヤーを取得 |
| `GET` | `/api/players/{id}` | ID でプレイヤーを取得 |
| `POST` | `/api/players` | プレイヤーを登録 |
| `PATCH` | `/api/players/{id}/score` | スコアを更新 |
| `GET` | `/api/leaderboard` | ランキング全件取得（Redis Cache-Aside） |
| `GET` | `/api/leaderboard?limit=10` | 上位 N 件を取得（N = 1～100、PostgreSQL を検索） |

### リクエスト例

**プレイヤーを登録：**

```bash
curl -i -X POST http://localhost:8080/api/players \
  -H 'Content-Type: application/json' \
  -d '{"id":1,"name":"Alice","score":1500}'
```

**スコアを更新：**

```bash
curl -i -X PATCH http://localhost:8080/api/players/1/score \
  -H 'Content-Type: application/json' \
  -d '{"score":2500}'
```

**ランキングを取得：**

```bash
curl -s http://localhost:8080/api/leaderboard
```

レスポンス例（実際の内容は DB の状態によって異なります）：

```json
[
  {"id": 1, "name": "Alice", "score": 2500}
]
```

**上位10件を取得：**

```bash
curl -s 'http://localhost:8080/api/leaderboard?limit=10'
```

### 主なバリデーションと HTTP ステータス

- プレイヤー ID が重複：`409 Conflict`
- 指定したプレイヤー ID が存在しない：`404 Not Found`
- 更新スコアが負数、または `score` がない：`400 Bad Request`
- `limit` が 1～100 の範囲外：`400 Bad Request`

## Redis キャッシュ Key

| Key | Redis 型 | 用途 |
| --- | --- | --- |
| `leaderboard:cache:v1` | Sorted Set | プレイヤー ID とスコア |
| `leaderboard:cache:v1:names` | Hash | プレイヤー ID と名前 |
| `leaderboard:cache:v1:ready` | String | キャッシュの準備完了を示すマーカー |

ランキングキャッシュの TTL は60秒です。空のランキングも Ready Marker によりキャッシュ済みと判定でき、その場合 Sorted Set と Hash が存在しないことがあります。

ローカル Redis の確認：

```bash
redis-cli EXISTS leaderboard:cache:v1:ready
redis-cli TTL leaderboard:cache:v1:ready
redis-cli ZRANGE leaderboard:cache:v1 0 -1 WITHSCORES
redis-cli HGETALL leaderboard:cache:v1:names
```

## テストと静的解析

テストの前に PostgreSQL と Redis を起動し、DB 接続情報を設定してください。

```bash
./mvnw test
```

コンパイル後に SpotBugs を実行：

```bash
./mvnw test-compile spotbugs:check
```

テストと静的解析の連続実行：

```bash
./mvnw test spotbugs:check
```

API / Controller、Service の単体テスト、PostgreSQL Repository の結合テスト、Redis 接続テスト、ランキングキャッシュのテストを実装しています。

## 継続的インテグレーション（CI）

[`.github/workflows/maven-test.yml`](.github/workflows/maven-test.yml) は `main` ブランチへの Push / Pull Request を契機に、以下を実行します。

1. PostgreSQL 17 と Redis 7 のサービスコンテナを起動します。
2. Temurin JDK 21 をセットアップします。
3. Redis への接続を確認します。
4. Maven / JUnit テストを実行します。
5. SpotBugs による静的解析を実行します。

実行履歴は [GitHub Actions](https://github.com/WUJINXIN/game-leaderboard-api/actions) を参照してください。

## ディレクトリ構成

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
  db/migration/                    # Flyway マイグレーション
src/test/java/com/example/gameleaderboard/
  ...                                # 単体・結合テスト
pom.xml
mvnw
```

## 今後の改善予定

- DB コミット後の Redis キャッシュ無効化における障害対応
- キャッシュ再構築・無効化時の並行実行制御
- `limit` 付きランキング取得における Redis の活用
- PostgreSQL の実際のコミット / ロールバックを確認する結合テストの追加
- 構造化ログの改善

## ライセンス

現在、リポジトリにはライセンスファイルがありません。別途許諾がない限り、権利はリポジトリの所有者に帰属します。
