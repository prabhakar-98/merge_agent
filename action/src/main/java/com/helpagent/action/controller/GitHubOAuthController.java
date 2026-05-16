package com.helpagent.action.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.helpagent.action.service.GitHubOAuthService;
import com.helpagent.action.service.OAuthToken;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for handling GitHub OAuth App authentication flow.
 * 
 * <p>Provides endpoints for:
 * <ul>
 *   <li>/oauth/login - Initiate OAuth flow</li>
 *   <li>/oauth/callback - Handle OAuth callback from GitHub</li>
 *   <li>/oauth/status - Check authentication status</li>
 *   <li>/oauth/logout - Revoke authentication</li>
 * </ul>
 */
@RestController
@RequestMapping("/oauth")
public class GitHubOAuthController {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthController.class);
    private static final String USER_SESSION_KEY = "github_user";

    private final GitHubOAuthService oAuthService;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public GitHubOAuthController(GitHubOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    /**
     * Initiate the OAuth flow by redirecting to GitHub authorization page.
     */
    @GetMapping("/login")
    public ResponseEntity<?> login() {
        if (!oAuthService.isOAuthConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "OAuth not configured",
                            "message", "GitHub OAuth App credentials are not configured. " +
                                    "Please set GITHUB_OAUTH_CLIENT_ID and GITHUB_OAUTH_CLIENT_SECRET."
                    ));
        }

        String state = UUID.randomUUID().toString();
        oAuthService.storeState(state);

        String authorizationUrl = oAuthService.getAuthorizationUrl(state);
        log.info("Redirecting to GitHub authorization URL");

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", authorizationUrl)
                .build();
    }

    /**
     * Handle the OAuth callback from GitHub after user authorization.
     */
    @GetMapping("/callback")
    public ResponseEntity<?> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription) {

        // Check for errors from GitHub
        if (error != null) {
            log.warn("OAuth authorization denied: {} - {}", error, errorDescription);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", error,
                            "message", errorDescription != null ? errorDescription : "Authorization was denied"
                    ));
        }

        // Validate the state parameter
        if (state == null || !oAuthService.validateAndConsumeState(state)) {
            log.warn("Invalid state parameter in OAuth callback");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "invalid_state",
                            "message", "Invalid state parameter. Please try logging in again."
                    ));
        }

        if (code == null || code.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "missing_code",
                            "message", "No authorization code received"
                    ));
        }

        try {
            // Exchange the code for an access token
            OAuthToken token = oAuthService.exchangeCodeForToken(code);

            // Get user information
            JsonNode userInfo = oAuthService.getAuthenticatedUser(token.accessToken());
            String userId = userInfo.get("id").asText();
            String username = userInfo.get("login").asText();

            // Store the token with username for multi-user support
            oAuthService.storeToken(userId, username, token);

            log.info("Successfully authenticated user: {} ({})", username, userId);

            // Create a one-time auth code for the frontend to exchange
            String authCode = oAuthService.createAuthCode(userId);
            String redirectUrl = frontendUrl + "/?auth_code=" + authCode;
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .build();

        } catch (Exception e) {
            log.error("Failed to complete OAuth flow: {}", e.getMessage(), e);
            // Redirect to frontend with error
            String errorRedirect = frontendUrl + "/?error=authentication_failed";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", errorRedirect)
                    .build();
        }
    }

    /**
     * Exchange a one-time auth code (from the callback redirect) for user info.
     * Called by the frontend after OAuth redirect.
     */
    @PostMapping("/exchange")
    public ResponseEntity<?> exchange(@RequestBody Map<String, String> body) {
        String authCode = body.get("code");
        if (authCode == null || authCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing_code",
                    "message", "No auth code provided"
            ));
        }

        String userId = oAuthService.exchangeAuthCode(authCode);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "invalid_code",
                    "message", "Invalid or expired auth code. Please log in again."
            ));
        }

        if (!oAuthService.hasValidToken(userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "token_invalid",
                    "message", "Token is no longer valid. Please log in again."
            ));
        }

        JsonNode userInfo = oAuthService.getAuthenticatedUser(oAuthService.getAccessToken(userId));
        String username = userInfo.get("login").asText();
        String name = userInfo.has("name") && !userInfo.get("name").isNull()
                ? userInfo.get("name").asText()
                : username;
        String avatarUrl = userInfo.has("avatar_url") ? userInfo.get("avatar_url").asText() : "";

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "user", Map.of(
                        "id", userId,
                        "username", username,
                        "name", name,
                        "avatar_url", avatarUrl
                )
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(HttpSession session) {
        @SuppressWarnings("unchecked")
        Map<String, String> userInfo = (Map<String, String>) session.getAttribute(USER_SESSION_KEY);

        if (userInfo == null) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", false,
                    "oauthConfigured", oAuthService.isOAuthConfigured()
            ));
        }

        String userId = userInfo.get("id");
        boolean hasValidToken = oAuthService.hasValidToken(userId);

        if (!hasValidToken) {
            session.removeAttribute(USER_SESSION_KEY);
            return ResponseEntity.ok(Map.of(
                    "authenticated", false,
                    "oauthConfigured", oAuthService.isOAuthConfigured()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "user", userInfo
        ));
    }

    /**
     * Log out the current user.
     *
     * @param session The HTTP session
     * @return Success response
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        @SuppressWarnings("unchecked")
        Map<String, String> userInfo = (Map<String, String>) session.getAttribute(USER_SESSION_KEY);

        if (userInfo != null) {
            String userId = userInfo.get("id");
            oAuthService.revokeToken(userId);
            log.info("Logged out user: {}", userInfo.get("username"));
        }

        session.invalidate();

        return ResponseEntity.ok(Map.of(
                "message", "Successfully logged out"
        ));
    }

    /**
     * Get the authorization URL without redirecting (for SPAs).
     */
    @GetMapping("/authorize-url")
    public ResponseEntity<?> getAuthorizeUrl() {
        if (!oAuthService.isOAuthConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "OAuth not configured",
                            "message", "GitHub OAuth App credentials are not configured."
                    ));
        }

        String state = UUID.randomUUID().toString();
        oAuthService.storeState(state);

        return ResponseEntity.ok(Map.of(
                "authorizationUrl", oAuthService.getAuthorizationUrl(state),
                "state", state
        ));
    }
}
