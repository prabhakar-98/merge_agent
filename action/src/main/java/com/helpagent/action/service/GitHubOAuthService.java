package com.helpagent.action.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpagent.action.config.GitHubOAuthProperties;
import com.helpagent.action.model.entity.OAuthTokenEntity;
import com.helpagent.action.repository.OAuthTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GitHubOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthService.class);

    private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private static final int REFRESH_BUFFER_SECONDS = 300;
    private static final long STATE_EXPIRY_MS = 600_000; // 10 minutes

    private final ConcurrentHashMap<String, Long> pendingStates = new ConcurrentHashMap<>();

    private final GitHubOAuthProperties oauthProperties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final WebClient apiClient;
    private final OAuthTokenRepository tokenRepository;

    public GitHubOAuthService(GitHubOAuthProperties oauthProperties, ObjectMapper objectMapper,
                              OAuthTokenRepository tokenRepository) {
        this.oauthProperties = oauthProperties;
        this.objectMapper = objectMapper;
        this.tokenRepository = tokenRepository;

        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "MergeHelpAgent/1.0")
                .build();

        this.apiClient = WebClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.USER_AGENT, "MergeHelpAgent/1.0")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public boolean isOAuthConfigured() {
        return oauthProperties.isConfigured();
    }

    public String getAuthorizationUrl(String state) {
        String scopes = oauthProperties.getScopes();

        return UriComponentsBuilder.fromUriString(GITHUB_AUTHORIZE_URL)
                .queryParam("client_id", oauthProperties.getClientId())
                .queryParam("redirect_uri", oauthProperties.getRedirectUri())
                .queryParam("scope", scopes)
                .queryParam("state", state)
                .queryParam("allow_signup", "true")
                .build()
                .toUriString();
    }

    public OAuthToken exchangeCodeForToken(String code) {
        log.info("Exchanging authorization code for access token");

        try {
            String response = webClient.post()
                    .uri(GITHUB_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData("client_id", oauthProperties.getClientId())
                            .with("client_secret", oauthProperties.getClientSecret())
                            .with("code", code)
                            .with("redirect_uri", oauthProperties.getRedirectUri()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode tokenData = objectMapper.readTree(response);

            if (tokenData.has("error")) {
                String error = tokenData.get("error").asText();
                String description = tokenData.has("error_description")
                        ? tokenData.get("error_description").asText()
                        : "Unknown error";
                log.error("Token exchange failed: {} - {}", error, description);
                throw new RuntimeException("OAuth token exchange failed: " + description);
            }

            String accessToken = tokenData.get("access_token").asText();
            String tokenType = tokenData.has("token_type")
                    ? tokenData.get("token_type").asText()
                    : "bearer";
            String scope = tokenData.has("scope")
                    ? tokenData.get("scope").asText()
                    : "";

            String refreshToken = tokenData.has("refresh_token")
                    ? tokenData.get("refresh_token").asText()
                    : null;
            int expiresIn = tokenData.has("expires_in")
                    ? tokenData.get("expires_in").asInt()
                    : 0;

            OAuthToken token;
            if (expiresIn > 0 && refreshToken != null) {
                token = OAuthToken.withExpiration(accessToken, tokenType, scope, refreshToken, expiresIn);
                log.info("OAuth token obtained with expiration (expires in {} seconds)", expiresIn);
            } else {
                token = OAuthToken.of(accessToken, tokenType, scope);
                log.info("OAuth token obtained (non-expiring)");
            }

            return token;

        } catch (Exception e) {
            log.error("Failed to exchange code for token: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange authorization code for token", e);
        }
    }

    public OAuthToken refreshAccessToken(String refreshToken) {
        log.info("Refreshing OAuth access token");

        try {
            String response = webClient.post()
                    .uri(GITHUB_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData("client_id", oauthProperties.getClientId())
                            .with("client_secret", oauthProperties.getClientSecret())
                            .with("grant_type", "refresh_token")
                            .with("refresh_token", refreshToken))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode tokenData = objectMapper.readTree(response);

            if (tokenData.has("error")) {
                String error = tokenData.get("error").asText();
                throw new RuntimeException("Token refresh failed: " + error);
            }

            String accessToken = tokenData.get("access_token").asText();
            String tokenType = tokenData.get("token_type").asText();
            String scope = tokenData.has("scope") ? tokenData.get("scope").asText() : "";
            String newRefreshToken = tokenData.has("refresh_token")
                    ? tokenData.get("refresh_token").asText()
                    : refreshToken;
            int expiresIn = tokenData.has("expires_in") ? tokenData.get("expires_in").asInt() : 0;

            log.info("OAuth token refreshed successfully");
            return OAuthToken.withExpiration(accessToken, tokenType, scope, newRefreshToken, expiresIn);

        } catch (Exception e) {
            log.error("Failed to refresh token: {}", e.getMessage());
            throw new RuntimeException("Failed to refresh OAuth token", e);
        }
    }

    public JsonNode getAuthenticatedUser(String accessToken) {
        log.debug("Fetching authenticated user info");

        try {
            String response = apiClient.get()
                    .uri("/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readTree(response);

        } catch (Exception e) {
            log.error("Failed to get user info: {}", e.getMessage());
            throw new RuntimeException("Failed to get authenticated user info", e);
        }
    }

    public void storeToken(String userId, String username, OAuthToken token) {
        OAuthTokenEntity entity = tokenRepository.findById(userId).orElse(new OAuthTokenEntity());
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setAccessToken(token.accessToken());
        entity.setTokenType(token.tokenType());
        entity.setScope(token.scope());
        entity.setRefreshToken(token.refreshToken());
        entity.setExpiresAt(token.expiresAt());
        tokenRepository.save(entity);
        log.debug("Token stored in database for user: {} ({})", username, userId);
    }

    public void storeToken(String userId, OAuthToken token) {
        storeToken(userId, null, token);
    }

    public String getAccessToken(String userId) {
        Optional<OAuthTokenEntity> entityOpt = tokenRepository.findById(userId);

        if (entityOpt.isEmpty()) {
            return null;
        }

        OAuthTokenEntity entity = entityOpt.get();

        if (entity.getExpiresAt() != null
                && Instant.now().plusSeconds(REFRESH_BUFFER_SECONDS).isAfter(entity.getExpiresAt())
                && entity.getRefreshToken() != null) {
            try {
                OAuthToken newToken = refreshAccessToken(entity.getRefreshToken());
                storeToken(userId, entity.getUsername(), newToken);
                return newToken.accessToken();
            } catch (Exception e) {
                log.warn("Failed to refresh token for user {}, removing stale entry", userId);
                tokenRepository.deleteById(userId);
                return null;
            }
        }

        if (entity.getExpiresAt() != null && Instant.now().isAfter(entity.getExpiresAt())) {
            log.warn("Token expired for user {} with no refresh token", userId);
            tokenRepository.deleteById(userId);
            return null;
        }

        return entity.getAccessToken();
    }

    public boolean hasValidToken(String userId) {
        Optional<OAuthTokenEntity> entityOpt = tokenRepository.findById(userId);

        if (entityOpt.isEmpty()) {
            return false;
        }

        OAuthTokenEntity entity = entityOpt.get();

        if (entity.getExpiresAt() != null && Instant.now().isAfter(entity.getExpiresAt())) {
            if (entity.getRefreshToken() != null) {
                try {
                    OAuthToken newToken = refreshAccessToken(entity.getRefreshToken());
                    storeToken(userId, entity.getUsername(), newToken);
                    return true;
                } catch (Exception e) {
                    tokenRepository.deleteById(userId);
                    return false;
                }
            }
            tokenRepository.deleteById(userId);
            return false;
        }

        return true;
    }

    public void revokeToken(String userId) {
        Optional<OAuthTokenEntity> entityOpt = tokenRepository.findById(userId);

        if (entityOpt.isPresent()) {
            OAuthTokenEntity entity = entityOpt.get();
            try {
                String credentials = oauthProperties.getClientId() + ":" + oauthProperties.getClientSecret();
                String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

                webClient.method(org.springframework.http.HttpMethod.DELETE)
                        .uri("https://api.github.com/applications/{client_id}/token",
                                oauthProperties.getClientId())
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(BodyInserters.fromValue("{\"access_token\":\"" + entity.getAccessToken() + "\"}"))
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block();

                log.info("Token revoked on GitHub for user: {}", userId);
            } catch (Exception e) {
                log.warn("Failed to revoke token on GitHub: {}", e.getMessage());
            }

            tokenRepository.deleteById(userId);
        }

        log.info("Token removed from database for user: {}", userId);
    }

    public OAuthToken getToken(String userId) {
        return tokenRepository.findById(userId)
                .map(entity -> {
                    if (entity.getExpiresAt() != null && entity.getRefreshToken() != null) {
                        return OAuthToken.withExpiration(
                                entity.getAccessToken(), entity.getTokenType(),
                                entity.getScope(), entity.getRefreshToken(),
                                (int) (entity.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond()));
                    }
                    return OAuthToken.of(entity.getAccessToken(), entity.getTokenType(), entity.getScope());
                })
                .orElse(null);
    }

    public long getActiveUserCount() {
        return tokenRepository.countByExpiresAtIsNullOrExpiresAtAfter(Instant.now());
    }

    public void storeState(String state) {
        pendingStates.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue() > STATE_EXPIRY_MS);
        pendingStates.put(state, System.currentTimeMillis());
    }

    public boolean validateAndConsumeState(String state) {
        Long createdAt = pendingStates.remove(state);
        if (createdAt == null) {
            return false;
        }
        return System.currentTimeMillis() - createdAt <= STATE_EXPIRY_MS;
    }

    private final ConcurrentHashMap<String, String> pendingAuthCodes = new ConcurrentHashMap<>();

    public String createAuthCode(String userId) {
        String code = UUID.randomUUID().toString();
        pendingAuthCodes.put(code, userId + "|" + System.currentTimeMillis());
        return code;
    }

    public String exchangeAuthCode(String code) {
        String value = pendingAuthCodes.remove(code);
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\|");
        long createdAt = Long.parseLong(parts[1]);
        if (System.currentTimeMillis() - createdAt > STATE_EXPIRY_MS) {
            return null;
        }
        return parts[0];
    }
}