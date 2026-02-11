@echo off
REM ============================================================
REM  AI Code Review Agent - Run Script
REM ============================================================
REM  This script sets environment variables and starts the
REM  Spring Boot application.
REM
REM  Usage:  run.bat
REM
REM  Before running, fill in your credentials below.
REM ============================================================

REM ─── GitHub Configuration ───
REM Generate a Personal Access Token at https://github.com/settings/tokens
REM Required scopes: repo, pull_requests
set GITHUB_TOKEN=your-github-token-here

REM The secret you configured in your GitHub webhook settings
set GITHUB_WEBHOOK_SECRET=your-webhook-secret-here

REM ─── Google AI Configuration ───
REM Get your API key from https://aistudio.google.com/apikey
set GOOGLE_API_KEY=your-google-api-key-here

REM Set to "true" to use Vertex AI instead of Google AI Studio
set GOOGLE_GENAI_USE_VERTEXAI=false

REM Only needed if GOOGLE_GENAI_USE_VERTEXAI=true
set GOOGLE_CLOUD_PROJECT_ID=your-gcp-project-id
set GOOGLE_CLOUD_LOCATION=us-central1

REM ============================================================
REM  Build and Run
REM ============================================================
echo.
echo ====================================
echo  AI Code Review Agent
echo ====================================
echo.
echo  GitHub Token:    %GITHUB_TOKEN:~0,8%***
echo  Google API Key:  %GOOGLE_API_KEY:~0,8%***
echo  Use Vertex AI:   %GOOGLE_GENAI_USE_VERTEXAI%
echo  Server Port:     8080
echo.
echo  Webhook URL:     http://localhost:8080/api/webhook/github
echo.
echo ====================================
echo  Building and starting...
echo ====================================
echo.

call mvnw.cmd spring-boot:run

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Application failed to start. Check the logs above.
    pause
)
