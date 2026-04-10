# CLAUDE.md

## Project Overview

**Merge Help Agent** is an AI-powered GitHub PR merge conflict analyzer and resolver.

### What it does
When a pull request is opened, updated, or reopened on GitHub, the agent automatically:
1. Receives the webhook event and verifies its HMAC-SHA256 signature
2. Fetches branch info and detects merge conflicts via the GitHub API
3. **Queries the RAG store** for similar past conflict resolution patterns
4. Reads the full file content from both branches to understand the conflict
5. Uses an LLM (Claude or Gemini) to analyze the conflict and generate a resolution
6. **Validates resolved code in a Docker sandbox** to catch syntax errors before committing
7. Creates a resolution branch (`merge-resolution/pr-{number}`), commits the resolved files, and comments on the PR with a detailed analysis
8. **Stores successful resolution patterns in the RAG** for future use
9. If resolution fails after 2 attempts, escalates to human review with a **detailed review.md report** summarizing what was attempted and which files need manual attention

### How it works
- A **GitHub webhook** triggers the flow. The controller validates the signature, parses the event, and hands off to the agent runner asynchronously (returns 202 immediately).
- The **Google ADK LoopAgent** orchestrates the LLM agent. It wraps an `LlmAgent` with retry logic (max 2 iterations). The LLM agent has access to 12 function tools split across two tool classes:
  - `MergeConflictTools` — 8 GitHub API tools (fetch branches, diffs, file content, create branches, commit files, comment, merge, exit loop)
  - `CodeReviewTools` — 4 new tools (sandbox validation, RAG search, RAG store, human review report)
- **Multi-model support** via the strategy pattern: `ModelStrategyFactory` selects the configured provider (Claude, Gemini, Ollama, LiteLLM) at startup. Each strategy knows how to build the appropriate model client.
- **Docker Sandbox** (`SandboxService`): Spins up Testcontainers with language-appropriate runtimes (OpenJDK, Python, Node.js) to compile/lint resolved code before committing.
- **RAG Pattern Store** (`ConflictPatternStore`): Uses LangChain4j `InMemoryEmbeddingStore` with `AllMiniLmL6V2` embedding model for vector similarity search. Patterns persist as JSON to `data/conflict-patterns/`.
- **Review Reports** (`ReviewReportService`): Generates structured Markdown review reports with conflict summaries, attempt history, sandbox results, and suggestions. Posted as PR comments on escalation.
- **OAuth**: supports per-user GitHub OAuth tokens (thread-local via `OAuthContext`) so the agent can act on behalf of the PR author. Falls back to default credentials if no user token is available.

## Tech Stack

- Java 21, Spring Boot 3.4.2, Maven
- Google ADK 0.5.0 (agent framework)
- Anthropic Claude SDK 1.0.0
- LangChain4j 0.33.0 (embeddings & RAG)
- Testcontainers 1.19.7 (Docker sandbox)
- Spring Security OAuth2, WebFlux

## Build & Run

```bash
./mvnw clean package                                    # build
./mvnw spring-boot:run                                  # run (default profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=devui # run with ADK Dev UI on port 8000
```

**Prerequisites**: Docker must be running for sandbox code validation.

## Project Structure

```
src/main/java/com/helpagent/action/
  agent/          # MergeHelpAgentConfig (ADK agent setup), MergeHelpAgentRunner (async/sync execution)
  config/         # AsyncConfig, GitHubOAuthProperties, ModelProperties, SecurityConfig, SandboxConfig, RagConfig
  controller/     # MergeWebhookController (webhook listener), GitHubOAuthController, MergeAnalysisController
  exception/      # GlobalExceptionHandler
  model/dto/      # PullRequestEvent, MergeConflictInfo, ConflictResolution, SandboxResult, ConflictPattern
  model/strategy/ # ModelStrategy interface + ClaudeModelStrategy, GeminiModelStrategy, ModelStrategyFactory
  rag/            # ConflictPatternStore (RAG vector store for conflict resolution patterns)
  security/       # WebhookSignatureVerifier (HMAC-SHA256)
  service/        # GitHubApiService (REST client), GitHubOAuthService, OAuthToken, SandboxService, ReviewReportService
  tools/          # MergeConflictTools (GitHub tools), CodeReviewTools (sandbox/RAG/review tools), OAuthContext
```

## Key Endpoints

- `POST /api/webhooks/github/merge` -- GitHub webhook receiver
- `POST /api/webhooks/github/merge/trigger` -- manual trigger for testing
- `POST /api/webhooks/github/merge/analyze` -- synchronous analysis
- `GET /api/webhooks/github/merge/health` -- health check
- `GET /api/oauth/github/authorize` -- start OAuth flow
- `GET /api/oauth/github/callback` -- OAuth callback

## Architecture Notes

- **Strategy pattern** for model selection (Claude, Gemini, Ollama, LiteLLM) via `ModelStrategyFactory`
- **LoopAgent** wraps the LLM agent with max 2 iterations; escalates to human review with review.md on failure
- **Async processing**: webhooks return 202 immediately, analysis runs on a thread pool (`AsyncConfig`)
- **OAuth context**: thread-local `OAuthContext` passes per-user tokens to agent tools
- **Docker Sandbox**: Testcontainers-based code validation catches syntax errors before commits
- **RAG Pattern Store**: LangChain4j in-memory embedding store learns from successful resolutions
- **Review Reports**: Structured Markdown reports generated on escalation with full diagnostic info
- Agent tools in `MergeConflictTools` and `CodeReviewTools` are exposed to the LLM via ADK `FunctionTool` annotations

## Agent Workflow

```
1. fetchBranchInfo (both branches)
2. detectMergeConflicts
3. fetchFileContent (conflicting files)
4. searchConflictPatterns (RAG query)           ← NEW
5. LLM analyzes + generates resolution
6. validateCodeInSandbox (each resolved file)   ← NEW
   └─ if fail → fix + re-validate (1 retry)
7. createResolutionBranch + commitResolvedFile
8. Code review: re-check diff quality
   └─ if issues → loop back (iteration 2)
9. storeConflictPattern (on success)            ← NEW
10. commentOnPullRequest + exitLoop

ON FAILURE AFTER 2 ITERATIONS:
11. generateHumanReviewReport (review.md)       ← NEW
12. exitLoop
```

## Environment Variables

Required:
- `GITHUB_WEBHOOK_SECRET` -- webhook HMAC secret
- `GITHUB_OAUTH_CLIENT_ID`, `GITHUB_OAUTH_CLIENT_SECRET` -- OAuth app credentials
- `CLAUDE_API_KEY` or `ANTHROPIC_API_KEY` -- for Claude provider
- `GEMINI_API_KEY` or `GOOGLE_API_KEY` -- for Gemini provider

Optional:
- `OLLAMA_BASE_URL` -- local model endpoint
- `LITELLM_API_KEY` -- LiteLLM proxy key
- `SANDBOX_WORKING_DIR` -- sandbox workspace directory (default: `./sandbox-workspace`)
- `RAG_STORAGE_PATH` -- RAG pattern storage path (default: `./data/conflict-patterns`)

## Code Conventions

- Config via `application.yml` (primary) and `application.properties` (env var overrides)
- Logging: DEBUG for `com.helpagent.action`, INFO for Spring
- DTOs use Jackson annotations; nested record-style classes in `PullRequestEvent`
- Tools use `@Schema` annotations for ADK tool documentation