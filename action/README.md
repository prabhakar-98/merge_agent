# Merge Help Agent

An AI-powered GitHub PR merge conflict analyzer and resolver. When a pull request has merge conflicts, the agent automatically analyzes them, generates resolutions, validates the code in a Docker sandbox, and commits the fix — or escalates to a human with a detailed diagnostic report.

## How It Works

```
GitHub PR Event
     │
     ▼
[1] Webhook received → HMAC verified → 202 Accepted
     │
     ▼ (async)
[2] Agent fetches branch info & detects conflicts
     │
     ▼
[3] RAG store queried for similar past resolutions
     │
     ▼
[4] LLM analyzes conflicts & generates resolution
     │
     ▼
[5] Docker sandbox validates resolved code (compile/lint)
     │  └─ if fail → LLM reads errors, fixes, re-validates
     │
     ▼
[6] Resolution branch created, files committed, PR commented
     │
     ▼
[7] Successful pattern stored in RAG for future use

On failure after 2 iterations:
     → Human review report posted on PR with full diagnostics
```

See [`docs/simplified-flow-diagram.puml`](docs/simplified-flow-diagram.puml) for the full sequence diagram.

## Features

- **Automatic Conflict Resolution**: Detects merge conflicts, reads both sides, and generates smart resolutions
- **Docker Sandbox Validation**: Compiles/lints resolved code before committing (Java, Python, Node.js)
- **RAG Pattern Store**: Learns from successful resolutions and uses past patterns to improve future ones
- **Human-in-the-Loop Escalation**: After 2 failed attempts, generates a structured review report with conflict summaries, attempt history, and suggestions
- **Multi-Model Support**: Strategy pattern for Claude, Gemini, OpenRouter, Ollama, and LiteLLM
- **React Dashboard**: Real-time monitoring of agent workflows, historical timeline, and ADK event tracking
- **GitHub OAuth**: Per-user authentication with token persistence
- **Async Processing**: Webhooks return 202 immediately; analysis runs on a configurable thread pool

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.4.2, Maven |
| Agent Framework | Google ADK 0.5.0 |
| LLM Providers | Anthropic Claude SDK 1.0.0, Gemini, OpenRouter, Ollama, LiteLLM |
| RAG | LangChain4j 0.33.0 (AllMiniLmL6V2 embeddings, in-memory vector store) |
| Sandbox | Testcontainers 1.19.7 (Docker) |
| Database | PostgreSQL 16 (Spring Data JPA) |
| Frontend | React 19, Vite, React Router |
| Security | Spring Security OAuth2, HMAC-SHA256 webhook verification |

## Project Structure

```
src/main/java/com/helpagent/action/
├── agent/           # MergeHelpAgentConfig, MergeHelpAgentRunner
├── config/          # AsyncConfig, SecurityConfig, ModelProperties, SandboxConfig, RagConfig
├── controller/      # MergeWebhookController, GitHubOAuthController, DashboardController
├── exception/       # GlobalExceptionHandler
├── model/
│   ├── dto/         # PullRequestEvent, MergeConflictInfo, ConflictResolution, SandboxResult, ConflictPattern
│   ├── entity/      # OAuthTokenEntity, AgentWorkflowEntity, AgentEventEntity
│   └── strategy/    # ModelStrategy, ClaudeModelStrategy, GeminiModelStrategy, ModelStrategyFactory
├── rag/             # ConflictPatternStore (vector similarity search)
├── repository/      # OAuthTokenRepository, AgentWorkflowRepository, AgentEventRepository
├── security/        # WebhookSignatureVerifier
├── service/         # GitHubApiService, GitHubOAuthService, SandboxService, ReviewReportService
└── tools/           # MergeConflictTools (8 GitHub tools), CodeReviewTools (4 review tools)
frontend/            # React dashboard (Vite)
```

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker (for PostgreSQL and sandbox validation)
- GitHub OAuth App credentials
- API key for at least one LLM provider

### Quick Start

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Configure environment (copy and edit .env.example)
cp .env.example .env

# 3. Build
./mvnw clean package

# 4. Run
./mvnw spring-boot:run

# 5. (Optional) Run with ADK Dev UI on port 8000
./mvnw spring-boot:run -Dspring-boot.run.profiles=devui
```

The backend runs on `http://localhost:8080`. The React dashboard runs on `http://localhost:5173` during development.

### Environment Variables

**Required:**

| Variable | Description |
|----------|-------------|
| `GITHUB_WEBHOOK_SECRET` | Webhook HMAC secret |
| `GITHUB_OAUTH_CLIENT_ID` | OAuth app client ID |
| `GITHUB_OAUTH_CLIENT_SECRET` | OAuth app client secret |
| `CLAUDE_API_KEY` or `ANTHROPIC_API_KEY` | For Claude provider |
| `GEMINI_API_KEY` or `GOOGLE_API_KEY` | For Gemini provider |

**Database:**

| Variable | Default |
|----------|---------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/mergehelpagent` |
| `DATABASE_USERNAME` | `mergehelpagent` |
| `DATABASE_PASSWORD` | `mergehelpagent` |

**Optional:**

| Variable | Description |
|----------|-------------|
| `OLLAMA_BASE_URL` | Local model endpoint |
| `LITELLM_API_KEY` | LiteLLM proxy key |
| `SANDBOX_WORKING_DIR` | Sandbox workspace (default: `./sandbox-workspace`) |
| `RAG_STORAGE_PATH` | RAG pattern storage (default: `./data/conflict-patterns`) |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/webhooks/github/merge` | GitHub webhook receiver |
| `POST` | `/api/webhooks/github/merge/trigger` | Manual trigger for testing |
| `POST` | `/api/webhooks/github/merge/analyze` | Synchronous analysis |
| `GET` | `/api/webhooks/github/merge/health` | Health check |
| `GET` | `/api/oauth/github/authorize` | Start OAuth flow |
| `GET` | `/api/oauth/github/callback` | OAuth callback |
| `GET` | `/api/dashboard/workflows/active` | Active workflows (for UI) |
| `GET` | `/api/dashboard/workflows/history` | Historical workflows (for UI) |
| `GET` | `/api/dashboard/stats` | Aggregate agent statistics |

### Webhook Configuration

In your GitHub App/webhook settings:
- **Payload URL**: `https://your-domain.com/api/webhooks/github/merge`
- **Content type**: `application/json`
- **Events**: Pull requests
- **Secret**: Must match `GITHUB_WEBHOOK_SECRET`

### Manual Trigger (for testing)

```bash
curl -X POST http://localhost:8080/api/webhooks/github/merge/trigger \
  -H "Content-Type: application/json" \
  -d '{"owner": "octocat", "repo": "hello-world", "prNumber": 1}'
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Merge Help Agent                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐    ┌──────────────┐    ┌───────────────────────┐  │
│  │  React UI    │◀──▶│  Controllers │───▶│  Agent Runner         │  │
│  │ (Dashboard)  │    │              │    │  (Async/Sync)         │  │
│  └──────────────┘    └──────┬───────┘    └───────────┬───────────┘  │
│                             │                        │              │
│                             ▼                        ▼              │
│  ┌────────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐    │
│  │ GitHubApiSvc   │ │  OAuth   │ │ Sandbox  │ │  RAG Store   │    │
│  │ (WebClient)    │ │  Service │ │ Service  │ │ (LangChain4j)│    │
│  └───────┬────────┘ └────┬─────┘ └────┬─────┘ └──────┬───────┘    │
│          │               │            │               │            │
└──────────┼───────────────┼────────────┼───────────────┼────────────┘
           ▼               ▼            ▼               ▼
    ┌─────────────┐  ┌───────────┐ ┌──────────┐ ┌──────────────┐
    │ GitHub API  │  │PostgreSQL │ │  Docker  │ │ File System  │
    └─────────────┘  └───────────┘ └──────────┘ └──────────────┘
```

### Agent Tools (12 total)

**MergeConflictTools** (8 GitHub API tools):
- `fetchBranchInfo`, `detectMergeConflicts`, `fetchFileContent`
- `createResolutionBranch`, `commitResolvedFile`, `commentOnPullRequest`
- `mergePullRequest`, `exitLoop`

**CodeReviewTools** (4 review tools):
- `validateCodeInSandbox` — Docker-based compilation/linting
- `searchConflictPatterns` — RAG similarity search
- `storeConflictPattern` — Save successful resolution to RAG
- `generateHumanReviewReport` — Structured escalation report

### Retry Strategy

1. **Per-file**: Sandbox validation fails → LLM reads error → fixes code → re-validates (1 retry)
2. **Per-iteration**: Code review finds issues → LoopAgent triggers iteration 2 (full re-analysis)
3. **Escalation**: After 2 failed iterations → diagnostic report posted on PR → human takes over

## Diagrams

- [`docs/simplified-flow-diagram.puml`](docs/simplified-flow-diagram.puml) — High-level conflict resolution flow


## License

MIT
