# TaskFlow

A task management REST API built with Java and Spring Boot. Users can register, log in, create projects, add tasks, and assign them to team members.

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Auth | JWT (jjwt) + bcrypt (cost 12) |
| Build | Maven |
| Container | Docker + Docker Compose |

> **Note:** The assignment prefers Go, but I chose Java as it's the language I'm most proficient in. This was explicitly permitted in the assignment brief.

---

## Architecture Decisions

### Layered Architecture
Simple Controller → Service → Repository pattern. Each layer has a clear responsibility:
- **Controllers** handle HTTP request/response mapping and validation
- **Services** contain business logic and authorization checks
- **Repositories** handle data access via Spring Data JPA

### Why Spring Boot?
Production-grade framework with built-in security, validation, and database migration support. Reduces boilerplate while keeping full control.

### Why Flyway?
Industry-standard migration tool for Java. Runs automatically on application startup — no manual steps needed. Versioned SQL migrations give full control over the schema.

### Auth Design
- JWT with 24-hour expiry, stored claims: `user_id` and `email`
- bcrypt with cost factor 12 (meets assignment requirement of ≥12)
- JWT secret loaded from environment variable, never hardcoded
- Stateless sessions — no server-side session storage

### Tradeoffs
- Used simple `String` for status/priority fields with DB CHECK constraints instead of JPA enums to keep the migration approach clean
- Seed data uses pre-computed bcrypt hash for `password123`
- Rollback migrations are provided as separate SQL files in `/db/rollback/` since Flyway Community doesn't support undo natively

---

## Running Locally

```bash
git clone https://github.com/your-name/taskflow
cd taskflow
cp .env.example .env
docker compose up --build
# API available at http://localhost:8080
```

That's it. One command. PostgreSQL, migrations, seed data — all handled automatically.

---

## Running Migrations

Migrations run **automatically** on application startup via Flyway. No manual steps required.

Migration files are in `backend/src/main/resources/db/migration/`:
- `V1__create_users_table.sql`
- `V2__create_projects_table.sql`
- `V3__create_tasks_table.sql`
- `V4__seed_data.sql`

Rollback scripts are in `backend/src/main/resources/db/rollback/` and can be run manually against the database if needed.

---

## Test Credentials

```
Email:    test@example.com
Password: password123
```

This user is created by the seed migration and owns a sample project with 3 tasks.

---

## API Reference

### Authentication

#### Register
```
POST /auth/register
Content-Type: application/json

{
  "name": "Jane Doe",
  "email": "jane@example.com",
  "password": "secret123"
}

→ 201 Created
{
  "token": "<jwt>",
  "user": { "id": "uuid", "name": "Jane Doe", "email": "jane@example.com" }
}
```

#### Login
```
POST /auth/login
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "password123"
}

→ 200 OK
{
  "token": "<jwt>",
  "user": { "id": "uuid", "name": "Test User", "email": "test@example.com" }
}
```

### Projects (requires `Authorization: Bearer <token>`)

| Method | Endpoint | Description |
|---|---|---|
| GET | `/projects?page=0&limit=20` | List accessible projects (paginated) |
| POST | `/projects` | Create project |
| GET | `/projects/:id` | Get project with tasks |
| PATCH | `/projects/:id` | Update project (owner only) |
| DELETE | `/projects/:id` | Delete project (owner only) |

### Tasks (requires `Authorization: Bearer <token>`)

| Method | Endpoint | Description |
|---|---|---|
| GET | `/projects/:id/tasks?status=todo&assignee=uuid&page=0&limit=20` | List tasks with filters |
| POST | `/projects/:id/tasks` | Create task |
| PATCH | `/tasks/:id` | Update task |
| DELETE | `/tasks/:id` | Delete task (project owner or task creator) |

### Bonus

| Method | Endpoint | Description |
|---|---|---|
| GET | `/projects/:id/stats` | Task counts by status and assignee |

### Error Responses
```json
// 400 Validation
{ "error": "validation failed", "fields": { "email": "is required" } }

// 401 Unauthenticated
{ "error": "unauthorized" }

// 403 Forbidden
{ "error": "forbidden" }

// 404 Not found
{ "error": "not found" }
```

A Postman collection is available at `postman/TaskFlow.postman_collection.json`.

---

## What I'd Do With More Time

1. **Integration tests** — Add tests with Testcontainers for auth, project, and task flows. Skipped to stay within time, but the architecture supports it easily.
2. **Rate limiting** — Add rate limiting on auth endpoints to prevent brute-force attacks.
3. **Refresh tokens** — Current design uses only access tokens. A refresh token flow would improve security.
4. **Audit trail** — Track who made what changes and when, beyond just `created_at`/`updated_at`.
5. **CORS configuration** — Currently not configured since there's no frontend, but would be needed for a real deployment.
6. **API docs** — Add Swagger/OpenAPI auto-generated documentation.
7. **Input sanitization** — Additional protection against XSS in text fields.
8. **Caching** — Redis for frequently accessed project/task data.
9. **WebSocket support** — Real-time task updates for connected clients.
