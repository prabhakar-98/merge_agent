# Architecture Planning

## Current Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Merge Help Agent                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐    ┌──────────────────┐    ┌───────────────────────┐  │
│  │  React UI    │◀──▶│  Controllers     │───▶│  Agent Runner         │  │
│  │ (Dashboard)  │    │  (Webhook,       │    │  (Async/Sync)         │  │
│  └──────────────┘    │   OAuth,         │    └───────────┬───────────┘  │
│                      │   Dashboard)     │                │              │
│                      └───────┬──────┬───┘                ▼              │
│                              │      │            ┌─────────────┐       │
│                              │      └───────────▶│  LLM Agent  │       │
│                              │                   └──────┬──────┘       │
│                              ▼                          │              │
│  ┌────────────────┐ ┌──────────┐ ┌──────────┐    ┌──────┴─────────┐   │
│  │ GitHubApiSvc   │ │  OAuth   │ │ Sandbox  │    │   Tools        │   │
│  │ (WebClient)    │ │  Service │ │ Service  │    │ (Merge/Review) │   │
│  └───────┬────────┘ └────┬─────┘ └────┬─────┘    └──────┬─────────┘   │
│          │               │            │                 │              │
└──────────┼───────────────┼────────────┼─────────────────┼──────────────┘
           ▼               ▼            ▼                 ▼               
    ┌─────────────┐  ┌───────────┐ ┌──────────┐ ┌───────────────────┐    
    │ GitHub API  │  │PostgreSQL │ │  Docker  │ │   File System     │    
    │ (REST)      │  │(Tokens/WF/│ │(Testcont)│ │   (patterns/)     │    
    └─────────────┘  │  Events)  │ └──────────┘ └───────────────────┘    
                     └───────────┘                                        
```

### Component Interactions

| Source | Target | Protocol | Purpose |
|--------|--------|----------|---------|
| GitHub | MergeWebhookController | HTTP POST (webhook) | PR events (opened, sync, reopened) |
| Controller | WebhookSignatureVerifier | In-process | HMAC-SHA256 validation |
| Controller | MergeHelpAgentRunner | Async (@Async) | Trigger analysis on thread pool |
| MergeHelpAgentRunner | PostgreSQL | JDBC/JPA | Persist ADK events + workflow status |
| React UI | DashboardController | HTTP GET | Poll active workflows, history, events |
| React UI | GitHubOAuthController | HTTP GET | Initiate OAuth login flow |
| AgentRunner | Google ADK LoopAgent | ADK API | Orchestrate LLM + tools |
| LoopAgent | ModelStrategyFactory | In-process | Select LLM provider |
| MergeConflictTools | GitHubApiService | In-process | GitHub REST calls |
| MergeConflictTools | OAuthContext | ThreadLocal | Per-user token resolution |
| CodeReviewTools | SandboxService | In-process | Docker code validation |
| CodeReviewTools | ConflictPatternStore | In-process | RAG query/store |
| CodeReviewTools | ReviewReportService | In-process | Escalation reports |
| SandboxService | Docker (Testcontainers) | Docker API | Spin up validation containers |
| ConflictPatternStore | File System | JSON I/O | Persist embeddings |
| GitHubOAuthService | PostgreSQL | JDBC/JPA | Token CRUD |

### Model Strategy Pattern

```
         ModelStrategy (interface)
              │
              ├── createModel(): BaseLlm
              ├── getProviderName(): String
              ├── getModelId(): String
              └── validateConfiguration(): void
              │
    ┌─────────┼─────────────┬──────────────┬──────────────┐
    ▼         ▼             ▼              ▼              ▼
 Claude    Gemini      OpenRouter      Ollama        LiteLLM
Strategy   Strategy     Strategy      Strategy      Strategy
```

**Current active provider**: OpenRouter (nvidia/llama-3.3-nemotron-super-49b-v1:free)

---

## Design Decisions

### 1. LoopAgent with Max 2 Iterations

**Decision**: Use Google ADK LoopAgent wrapping an LlmAgent, capped at 2 iterations.

**Rationale**: Merge conflicts are inherently complex, but unbounded retries waste tokens and time. Two iterations allow one retry after sandbox validation failure or code review feedback, then escalate to human review.

**Trade-off**: Simpler conflicts may be over-engineered (1 iteration would suffice), complex conflicts may need more attempts. The 2-iteration cap prioritizes cost control and time-to-response over resolution rate.

### 2. In-Memory RAG with File Persistence

**Decision**: LangChain4j InMemoryEmbeddingStore persisted as JSON to disk.

**Rationale**: For an MVP, avoids the operational overhead of running a dedicated vector database (Pinecone, Weaviate, Chroma). Patterns are relatively small and fast startup matters.

**Trade-off**: No concurrent write safety across instances (single-instance only), no ANN indexing (brute-force cosine similarity), memory-bound pattern capacity.

### 3. Testcontainers for Sandbox Validation

**Decision**: Spin up disposable Docker containers per validation request using Testcontainers.

**Rationale**: Complete isolation between validations. No persistent state. No shared runtime contamination. Supports multiple languages with a single abstraction.

**Trade-off**: Cold-start latency (~2-5s per container). Higher resource usage than a persistent validation service. Requires Docker daemon on the host.

### 4. Thread-Local OAuth Context

**Decision**: Pass per-user OAuth tokens via ThreadLocal (OAuthContext).

**Rationale**: Google ADK tools don't have a built-in mechanism to pass per-request state. ThreadLocal bridges the gap between the controller (where user identity is known) and the tool methods (where tokens are needed).

**Trade-off**: Fragile if execution crosses thread boundaries unexpectedly. Must be cleared in finally blocks. Not compatible with reactive/virtual thread models without adaptation.

### 5. Async Webhook Processing

**Decision**: Webhooks return 202 immediately; analysis runs on a configurable thread pool.

**Rationale**: GitHub expects webhook responses within 10 seconds. Merge conflict analysis can take 30-120+ seconds. Async decouples the two.

**Trade-off**: No synchronous success/failure feedback to GitHub. Must rely on PR comments for status communication. Retry logic lives in the agent, not the webhook handler.

### 6. React Frontend + ADK Event Tracking

**Decision**: A standalone Vite + React dashboard that tracks live agent runs by capturing every ADK `Event` from `Flowable<Event>` and saving them to PostgreSQL.

**Rationale**: Gives users visibility into the "black box" of the LLM agent. PostgreSQL acts as the single source of truth for workflow state and event history.

**Trade-off**: Increases database write volume (one write per tool call/text response). Requires polling from the frontend.

---

## Planned Refactors

### Priority 1: Immediate Improvements

| Item | Current State | Target State | Effort | Status |
|------|--------------|--------------|--------|--------|
| Rename `openrouter.java` | Violates Java naming conventions | `OpenRouterModelStrategy.java` | Small | Pending |
| Extract tool registration | Hardcoded tool list in AgentConfig | Annotation-driven discovery | Medium | Pending |
| Add health endpoint for dependencies | Only basic `/health` | Check DB, Docker, GitHub API connectivity | Small | Pending |
| Error classification + two-tier notification | Generic exception handling, silent failures | Typed errors + guaranteed PR comment on failure | Medium | ✅ Done |

### Priority 2: Scalability

| Item | Current State | Target State | Effort |
|------|--------------|--------------|--------|
| RAG persistence | In-memory + JSON file | PostgreSQL pgvector or external vector DB | Large |
| Sandbox pool | Cold-start per request | Warm container pool with reuse | Medium |
| Event queue | Direct async call | Redis/RabbitMQ message queue for webhook events | Large |
| Multi-instance support | Single-instance (file-based RAG) | Stateless services + shared persistence | Large |

### Priority 3: Feature Extensions

| Item | Description | Effort |
|------|-------------|--------|
| Streaming resolution updates | Real-time progress via SSE/WebSocket on PR comments | Medium |
| Multi-file conflict orchestration | Coordinate resolutions across interdependent files | Large |
| Custom model per repository | Let repo owners configure preferred LLM via `.mergehelpagent.yml` | Medium |
| Resolution confidence scoring | LLM outputs confidence; skip commit if below threshold | Small |
| Webhook retry handling | Idempotency keys to handle GitHub webhook retries | Small |

---

## Component Dependency Graph

```
ActionApplication (entry point)
 └── SecurityConfig
 └── AsyncConfig
 └── ModelProperties
 └── RagConfig, SandboxConfig
 └── MergeWebhookController
      └── WebhookSignatureVerifier
      └── MergeHelpAgentRunner
           └── MergeHelpAgentConfig
                ├── ModelStrategyFactory
                │    └── [Claude|Gemini|OpenRouter]ModelStrategy
                ├── MergeConflictTools
                │    ├── GitHubApiService (WebClient → GitHub REST)
                │    └── OAuthContext (ThreadLocal)
                └── CodeReviewTools
                     ├── SandboxService (Testcontainers → Docker)
                     ├── ConflictPatternStore (LangChain4j → JSON)
                     └── ReviewReportService
  └── GitHubOAuthController
       └── GitHubOAuthService
            └── OAuthTokenRepository (JPA → PostgreSQL)
  └── DashboardController
       └── AgentWorkflowRepository, AgentEventRepository (JPA → PostgreSQL)
React Frontend (Vite)
 └── App.jsx
      └── Dashboard, WorkflowDetail, Header (Polls DashboardController)
```

---

## Data Flow: Webhook to Resolution

```
GitHub PR Event
     │
     ▼
[1] POST /api/webhooks/github/merge
     │ ── verify HMAC signature
     │ ── parse PullRequestEvent
     │ ── return 202 Accepted
     │
     ▼ (async thread pool)
[2] MergeHelpAgentRunner.runAsync()
     │ ── set OAuthContext
     │ ── create ADK session with context vars
     │
     ▼
[3] LoopAgent.run() — iteration 1
     │
     ├── ADK Event: Tool Call: fetchBranchInfo(base) → Persist to PostgreSQL
     ├── ADK Event: Tool Response → Persist
     ├── ADK Event: Tool Call: detectMergeConflicts() → Persist
     │
     ├── FOR EACH conflicting file:
     │   ├── ADK Event: Tool Call: fetchFileContent(...) → Persist
     │   ├── ADK Event: Tool Call: searchConflictPatterns(...)  ← RAG
     │   ├── LLM generates resolved content
     │   ├── ADK Event: Tool Call: validateCodeInSandbox(...)      ← Docker
     │   │   └── if fail: LLM fixes + re-validates (1 retry)
     │   ├── ADK Event: Tool Call: createResolutionBranch()
     │   └── ADK Event: Tool Call: commitResolvedFile(...)
     │
     ├── Code review check (diff quality)
     │   └── if issues found → iteration 2
     │
     ├── ADK Event: Tool Call: storeConflictPattern(...)         ← RAG
     ├── ADK Event: Tool Call: commentOnPullRequest(...)
     └── exitLoop()

[4] ON FAILURE (after 2 iterations):
     ├── ADK Event: Tool Call: generateHumanReviewReport()        ← Markdown (Sets HUMAN_IN_LOOP)
     ├── ADK Event: Tool Call: commentOnPullRequest(...)          ← Escalation posted
     └── exitLoop()
```

---

## Error Handling Strategy

### Error Classification

When the agent fails, exceptions are classified into categories for targeted notification and recovery:

| Category | Trigger | User-Facing Message |
|----------|---------|---------------------|
| `AUTH_EXPIRED` | 401/403 from GitHub, or token-related exception | Re-authorize via OAuth endpoint |
| `RATE_LIMITED` | 429 from GitHub | Auto-retry on next push, no action needed |
| `GITHUB_UNAVAILABLE` | 5xx from GitHub | Transient — retry by pushing a commit |
| `TIMEOUT` | Request timeout / timed out message | PR may be too large, consider splitting |
| `SANDBOX_UNAVAILABLE` | Docker/container errors | Admin should check Docker daemon |
| `LLM_ERROR` | Model/API key issues | Check provider config or retry later |
| `INTERNAL_ERROR` | Anything else | Generic fallback message |

### Two-Tier Failure Notification

Ensures the PR **always** gets visible feedback — no silent failures:

```
Exception caught in MergeHelpAgentRunner
    │
    ▼
[Tier 1] Rich failure report via ReviewReportService
    │ ── classifyError(e) → category
    │ ── generateReviewReport(...) with category-specific suggestion
    │ ── postReviewAsComment(...)
    │
    │ (if Tier 1 fails — e.g., GitHub API itself is the problem)
    ▼
[Tier 2] Simple direct comment via GitHubApiService.commentOnPR
    │ ── minimal markdown: error type + truncated details
    │
    │ (if Tier 2 also fails)
    ▼
[Tier 3] Log only — error is logged with full context for ops debugging
```

### Design Decisions

- **Two-tier fallback**: The rich report uses `ReviewReportService` which itself calls GitHub API. If GitHub is down, Tier 1 fails. Tier 2 uses a direct `commentOnPR` call as a simpler retry path (may succeed if the issue was transient).
- **Error classification via pattern matching**: Uses `instanceof` for `WebClientResponseException` (HTTP status codes) and message substring matching for other exceptions. Pragmatic over perfect — covers 90% of cases.
- **Truncation**: Error messages are capped at 500 chars in PR comments to avoid noise.
- **No retry loop**: A single retry (Tier 2) is acceptable. Multiple retries for the notification itself would risk spamming the PR if the issue is intermittent.

---

## Risk Register

| Risk | Impact | Likelihood | Mitigation |
|------|--------|-----------|------------|
| GitHub API rate limiting | Agent stalls mid-resolution | Medium | Implement exponential backoff; cache branch info |
| Docker daemon unavailable | Sandbox validation skipped | Low | Graceful fallback (skip validation, flag in comment) |
| LLM generates invalid code | Commit broken resolution | Medium | Sandbox validation gate (already implemented) |
| OAuth token expiry mid-resolution | API calls fail | Low | Token refresh before agent run; retry on 401 |
| RAG pattern poisoning | Bad patterns recommended | Low | Confidence threshold filtering; human review on low-confidence |
| Large file conflicts (>100KB) | LLM context overflow | Medium | Chunk files; only send conflict regions, not full content |
| Concurrent PRs on same repo | Branch naming conflicts | Low | Include timestamp/UUID in resolution branch names |

---

## Technology Constraints

- **Java 21**: Required for virtual threads (future), pattern matching, record types
- **Spring Boot 3.4.2**: Provides dependency injection, web framework, JPA, async
- **Google ADK 0.5.0**: Agent orchestration framework — constrains tool registration and session model
- **Docker**: Required at runtime for sandbox validation and PostgreSQL
- **Single-instance deployment**: Current RAG file persistence doesn't support multi-instance

---

## Future Architecture Vision

```
Phase 1 (Current):  Single instance, in-memory RAG, direct webhook processing
Phase 2 (Next):     Message queue, pgvector RAG, warm container pool
Phase 3 (Scale):    Kubernetes deployment, distributed tracing, multi-tenant isolation
```