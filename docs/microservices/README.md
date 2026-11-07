# Game Leaderboard Microservices

## Goal

Evolve the existing Spring Boot monolith into independently
deployable services while preserving CI/CD quality gates.

## Planned Services

- Player Service: Player CRUD with PostgreSQL
- Leaderboard Service: Ranking queries with Redis
- API Gateway: Unified API entry point
- RabbitMQ: Asynchronous score update events

## Development Principles

- One business responsibility per service
- Independent deployment
- No direct cross-service database access
- Automated tests and quality gates
- Immutable container image deployment
