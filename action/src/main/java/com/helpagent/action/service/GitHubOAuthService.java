package com.helpagent.action.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpagent.action.config.GitHubOAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling GitHub OAuth 2.0 authentication flow.
 * 
 * <p>This service implements the OAuth 2.0 Authorization Code Grant flow:
 * <ol>
 *   <li>Generate authorization URL → User redirected to GitHub</li>
 *   <li>User authorizes the app → GitHub redirects back with code</li>
 *   <li>Exchange code for access token</li>
 *   <li>Use token to access GitHub API on behalf of user</li>
 * </ol>
 * 
 * <p>Token storage is in-memory by default. For production, consider using
 * a persistent store (database, Redis) with encryption.
 * 
 * @see <a href="https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps">GitHub OAuth Apps</a>
 * @see <a href="https://docs.github.com/en/rest/about-the-rest-api/about-the-rest-api">GitHub REST API</a>
 */
@Service
public class GitHubOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthService.class);
    
    private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_API_BASE = "https://api.github.com";
    
    // Token refresh buffer: refresh 5 minutes before expiry
    private static final int REFRESH_BUFFER_SECONDS = 300;

    private final GitHubOAuthProperties oauthProperties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final WebClient apiClient;

    // In-memory token storage: userId -> token
    // For production, use a persistent encrypted store
    private final Map<String, OAuthToken> tokenStore = new ConcurrentHashMap<>();

    public GitHubOAuthService(GitHubOAuthProperties oauthProperties, ObjectMapper objectMapper) {
        this.oauthProperties = oauthProperties;
        this.objectMapper = objectMapper;
        
        // Client for token exchange (github.com)
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "MergeHelpAgent/1.0")
                .build();
        
        // Client for API calls (api.github.com)
        this.apiClient = WebClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.USER_AGENT, "MergeHelpAgent/1.0")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    /**
     * Checks if OAuth is properly configured.
     */
    public boolean isOAuthConfigured() {
        return oauthProperties.isConfigured();
    }

    /**
     * Generates the GitHub authorization URL for the OAuth flow.
     * 
     * @param state A random string to prevent CSRF attacks (must be verified on callback)
     * @return The full authorization URL to redirect the user to
     */
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

    /**
     * Exchanges an authorization code for an access token.
     * 
     * @param code The authorization code received from GitHub
     * @return The OAuth token
     * @throws RuntimeException if the token exchange fails
     */
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

            // Check for error
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

            // Handle optional expiration (for GitHub Apps with expiring tokens)
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

    /**
     * Refreshes an expired OAuth token using the refresh token.
     * 
     * @param refreshToken The refresh token
     * @return The new OAuth token
     */
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

    /**
     * Gets the authenticated user's information.
     * 
     * @param accessToken The access token
     * @return User data as JSON
     */
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

    /**
     * Stores a token for a user.
     * 
     * @param userId The GitHub user ID
     * @param token The OAuth token
     */
    public void storeToken(String userId, OAuthToken token) {
        tokenStore.put(userId, token);
        log.debug("Token stored for user: {}", userId);
    }

    /**
     * Retrieves a valid token for a user, refreshing if necessary.
     * 
     * @param userId The GitHub user ID
     * @return The access token, or null if not found or invalid
     */
    public String getAccessToken(String userId) {
        OAuthToken token = tokenStore.get(userId);
        
        if (token == null) {
            return null;
        }

        // Check if token needs refresh
        if (token.expiresWithin(REFRESH_BUFFER_SECONDS) && token.refreshToken() != null) {
            try {
                OAuthToken newToken = refreshAccessToken(token.refreshToken());
                tokenStore.put(userId, newToken);
                return newToken.accessToken();
            } catch (Exception e) {
                log.warn("Failed to refresh token for user {}, token may be expired", userId);
                tokenStore.remove(userId);
                return null;
            }
        }

        // Check if token is expired without refresh capability
        if (token.isExpired()) {
            log.warn("Token expired for user {} with no refresh token", userId);
            tokenStore.remove(userId);
            return null;
        }

        return token.accessToken();
    }

    /**
     * Checks if a user has a valid token.
     * 
     * @param userId The GitHub user ID
     * @return true if the user has a valid (non-expired) token
     */
    public boolean hasValidToken(String userId) {
        OAuthToken token = tokenStore.get(userId);
        if (token == null) {
            return false;
        }
        
        // If token is expired but has refresh token, try to refresh
        if (token.isExpired() && token.refreshToken() != null) {
            try {
                OAuthToken newToken = refreshAccessToken(token.refreshToken());
                tokenStore.put(userId, newToken);
                return true;
            } catch (Exception e) {
                tokenStore.remove(userId);
                return false;
            }
        }
        
        return !token.isExpired();
    }

    /**
     * Revokes a user's token (removes from store and optionally revokes on GitHub).
     * 
     * @param userId The GitHub user ID
     */
    public void revokeToken(String userId) {
        OAuthToken token = tokenStore.remove(userId);
        
        if (token != null) {
            // Optionally revoke the token on GitHub
            // This requires Basic auth with client_id:client_secret
            try {
                String credentials = oauthProperties.getClientId() + ":" + oauthProperties.getClientSecret();
                String basicAuth = Base64Encoder.encode(credentials);

                webClient.method(org.springframework.http.HttpMethod.DELETE)
                        .uri("https://api.github.com/applications/{client_id}/token",
                             oauthProperties.getClientId())
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(BodyInserters.fromValue("{\"access_token\":\"" + token.accessToken() + "\"}"))
                        .retrieve()
                        .bodyToMono(Void.class)
                        .block();

                log.info("Token revoked on GitHub for user: {}", userId);
            } catch (Exception e) {
                // Token revocation is best-effort
                log.warn("Failed to revoke token on GitHub: {}", e.getMessage());
            }
        }
        
        log.info("Token removed from store for user: {}", userId);
    }

    /**
     * Gets the stored token object for a user (for checking expiration, scopes, etc.)
     */
    public OAuthToken getToken(String userId) {
        return tokenStore.get(userId);
    }

    // Simple Base64 encoder helper
    private static class Base64Encoder {
        static String encode(String input) {
            return java.util.Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
        }
    }
}
