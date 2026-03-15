# Merge Help Agent

An AI-powered GitHub PR merge conflict analyzer built with Spring Boot, Google ADK, and multi-model support (Claude, Gemini).

## Features

- **GitHub Webhook Integration**: Automatically triggers on PR events (opened, synchronize, reopened, closed)
- **Multi-Model Support**: Configurable AI backends via strategy pattern
  - Claude (Anthropic)
  - Gemini (Google)
- **OAuth Authentication**: GitHub OAuth flow for secure API access
- **Async Processing**: Non-blocking webhook handling with async analysis
- **HMAC Signature Verification**: Secure webhook validation using SHA-256

## Tech Stack

- **Java 21**
- **Spring Boot 3.4.2**
- **Google ADK (Agent Development Kit) 0.5.0**
- **Anthropic Claude SDK 1.0.0**
- **Maven**

## Project Structure

```
src/main/java/com/helpagent/action/
├── ActionApplication.java          # Main application entry
├── agent/                          # AI agent configuration
│   ├── MergeHelpAgentConfig.java
│   └── MergeHelpAgentRunner.java
├── config/                         # Application configuration
│   ├── AsyncConfig.java
│   ├── GitHubOAuthProperties.java
│   ├── ModelProperties.java
│   └── SecurityConfig.java
├── controller/                     # REST controllers
│   ├── GitHubOAuthController.java
│   ├── MergeAnalysisController.java
│   └── MergeWebhookController.java
├── exception/
│   └── GlobalExceptionHandler.java
├── model/
│   ├── dto/                        # Data transfer objects
│   │   ├── ConflictResolution.java
│   │   ├── MergeConflictInfo.java
│   │   └── PullRequestEvent.java
│   └── strategy/                   # Model strategy pattern
│       ├── AbstractModelStrategy.java
│       ├── ClaudeModelStrategy.java
│       ├── GeminiModelStrategy.java
│       ├── ModelStrategy.java
│       └── ModelStrategyFactory.java
├── security/
│   └── WebhookSignatureVerifier.java
├── service/
│   ├── GitHubApiService.java
│   ├── GitHubOAuthService.java
│   └── OAuthToken.java
└── tools/
    ├── MergeConflictTools.java
    └── OAuthContext.java
```

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+
- GitHub App credentials
- API keys for Claude or Gemini

### Configuration

Create `application.yml` or set environment variables:

```yaml
github:
  webhook-secret: your-webhook-secret
  oauth:
    client-id: your-client-id
    client-secret: your-client-secret

model:
  provider: claude  # or 'gemini'

anthropic:
  api-key: your-anthropic-api-key

# For Gemini
# google:
#   ai:
#     api-key: your-google-api-key
```

### Running the Application

```bash
# Using Maven wrapper
./mvnw spring-boot:run

# Or build and run JAR
./mvnw clean package
java -jar target/action-0.0.1-SNAPSHOT.jar
```

### Development UI

Run with the `devui` profile to enable Google ADK's development interface:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=devui
```

## API Endpoints

### Webhook Endpoint

```
POST /api/webhooks/github/merge
```

Configure this URL in your GitHub App webhook settings:
- **Payload URL**: `https://your-domain.com/api/webhooks/github/merge`
- **Content type**: `application/json`
- **Events**: Pull requests
- **Secret**: Match `github.webhook-secret` in config

### Manual Trigger

```
POST /api/webhooks/github/merge/trigger
Content-Type: application/json

{
  "owner": "octocat",
  "repo": "hello-world",
  "prNumber": 1
}
```

### OAuth Endpoints

```
GET  /api/oauth/github/authorize   # Start OAuth flow
GET  /api/oauth/github/callback    # OAuth callback
```

## License

MIT
