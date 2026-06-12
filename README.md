# AI Image Platform (Spring Boot)

Enterprise-grade AI image generation platform (monolith) built with **Spring Boot 3.5.x + Java 17**, integrating **Redis queue** + **Python Bridge (ComfyUI)**.

> This repository is generated incrementally in phases. Current version includes:
> - User register/login
> - JWT auth + Redis token store
> - Image task table + status machine
> - Redis async queue (producer/worker skeleton + retry/dlq)
> - Python Bridge client (WebClient)
> - WebSocket push for task status
> - JUnit5/Mockito/MockMvc tests + JaCoCo coverage gate

---

## Tech Stack

- Java 17
- Spring Boot 3.5.x
- Spring Security + JWT
- Redis 7 (token store + async task queue)
- MySQL 8 (users, image_tasks)
- MyBatis Plus
- WebSocket (task status push)
- Swagger (springdoc-openapi)
- Docker Compose

---

## Quick Start

### 1) Start MySQL + Redis

```bash
cd ai-image-platform
docker compose up -d
```

MySQL will create database: `ai_image_platform` with root password `root`.

### 2) Run Spring Boot

Open project with IntelliJ IDEA and run:

- `com.example.aiplatform.AiImagePlatformApplication`

Spring Boot will auto-init schema via `classpath:schema.sql`.

### 3) Swagger

- http://localhost:8080/swagger-ui.html

---

## API

### User

- `POST /api/user/register`
- `POST /api/user/login`
- `POST /api/user/logout`

JWT token is returned from login:

```json
{"code":0,"message":"OK","data":{"token":"...","expireSeconds":604800}}
```

### Tasks

- `GET /api/tasks/{id}` (requires JWT)
- Status machine demo endpoints:
  - `POST /api/tasks/{id}/processing`
  - `POST /api/tasks/{id}/done`
  - `POST /api/tasks/{id}/failed?reason=xxx`

> Note: `/api/tasks/submit` and `/api/tasks/my` are placeholders until principal contains userId.

---

## WebSocket

Connect:

```
ws://localhost:8080/ws/tasks?token=Bearer%20<JWT>
```

Server pushes task status changes:

```json
{
  "taskId": 1,
  "userId": 10,
  "status": "PROCESSING",
  "message": "Task processing",
  "time": "2026-06-12T19:40:00"
}
```

---

## Redis Keys

- Token store
  - `auth:token:{userId}:{jti}` -> "1" (TTL = JWT expire)
  - `auth:sso:{userId}` -> jti (optional)

- Task queue
  - `image:task:queue` (List)
  - `image:task:retry:zset` (ZSET, score = next execute timestamp)
  - `image:task:dlq` (List)

---

## Tests & Coverage

Tests:

- `UserServiceTest` (Mockito)
- `TaskServiceTest` (status machine)
- `BridgeClientTest` (MockWebServer)
- `ApiMockMvcTest` (register/login + security)

Coverage gate:
- JaCoCo minimum instruction covered ratio: **70%**

Run in IDEA Maven or CLI:

```bash
mvn test
```

> If tests require real MySQL/Redis, start docker-compose first.

---

## Next Improvements

- Put userId into Security principal, implement `/api/tasks/submit` + enqueue + `/api/tasks/my`
- Worker calls Python Bridge then marks DONE/FAILED + retries/DLQ
- Add callback endpoint for Python Bridge
- Add more robust logging/metrics/observability

---

## License

Internal / demo use.
