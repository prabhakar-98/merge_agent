package com.helpagent.action.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.helpagent.action.service.GitHubOAuthService;
import com.helpagent.action.service.OAuthToken;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final String STATE_SESSION_KEY = "oauth_state";
    private static final String USER_SESSION_KEY = "github_user";

    private final GitHubOAuthService oAuthService;

    public GitHubOAuthController(GitHubOAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    /**
     * Initiate the OAuth flow by redirecting to GitHub authorization page.
     *
     * @param session The HTTP session to store the state parameter
     * @return Redirect response to GitHub
     */
    @GetMapping("/login")
    public ResponseEntity<?> login(HttpSession session) {
        if (!oAuthService.isOAuthConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "OAuth not configured",
                            "message", "GitHub OAuth App credentials are not configured. " +
                                    "Please set GITHUB_OAUTH_CLIENT_ID and GITHUB_OAUTH_CLIENT_SECRET."
                    ));
        }

        // Generate a random state parameter to prevent CSRF attacks
        String state = UUID.randomUUID().toString();
        session.setAttribute(STATE_SESSION_KEY, state);

        String authorizationUrl = oAuthService.getAuthorizationUrl(state);
        log.info("Redirecting to GitHub authorization URL");

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", authorizationUrl)
                .build();
    }

    /**
     * Handle the OAuth callback from GitHub after user authorization.
     *
     * @param code  The authorization code from GitHub
     * @param state The state parameter for CSRF validation
     * @param error The error code if authorization was denied
     * @param errorDescription Description of the error
     * @param session The HTTP session
     * @return Success or error response
     */
    @GetMapping("/callback")
    public ResponseEntity<?> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription,
            HttpSession session) {

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
        String storedState = (String) session.getAttribute(STATE_SESSION_KEY);
        if (storedState == null || !storedState.equals(state)) {
            log.warn("Invalid state parameter in OAuth callback");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "invalid_state",
                            "message", "Invalid state parameter. Please try logging in again."
                    ));
        }

        // Clear the state from session
        session.removeAttribute(STATE_SESSION_KEY);

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

            // Store the token
            oAuthService.storeToken(userId, token);

            // Store user info in session
            session.setAttribute(USER_SESSION_KEY, Map.of(
                    "id", userId,
                    "username", username,
                    "name", userInfo.has("name") && !userInfo.get("name").isNull() 
                            ? userInfo.get("name").asText() 
                            : username,
                    "avatar_url", userInfo.has("avatar_url") 
                            ? userInfo.get("avatar_url").asText() 
                            : ""
            ));

            log.info("Successfully authenticated user: {} ({})", username, userId);

            return ResponseEntity.ok(Map.of(
                    "message", "Successfully authenticated",
                    "user", Map.of(
                            "id", userId,
                            "username", username,
                            "name", userInfo.has("name") && !userInfo.get("name").isNull() 
                                    ? userInfo.get("name").asText() 
                                    : username
                    )
            ));

        } catch (Exception e) {
            log.error("Failed to complete OAuth flow: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "authentication_failed",
                            "message", "Failed to complete authentication: " + e.getMessage()
                    ));
        }
    }

    /**
     * Check the current authentication status.
     *
     * @param session The HTTP session
     * @return Authentication status and user info if authenticated
     */
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
     *
     * @param session The HTTP session
     * @return The authorization URL
     */
    @GetMapping("/authorize-url")
    public ResponseEntity<?> getAuthorizeUrl(HttpSession session) {
        if (!oAuthService.isOAuthConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "OAuth not configured",
                            "message", "GitHub OAuth App credentials are not configured."
                    ));
        }

        String state = UUID.randomUUID().toString();
        session.setAttribute(STATE_SESSION_KEY, state);

        return ResponseEntity.ok(Map.of(
                "authorizationUrl", oAuthService.getAuthorizationUrl(state),
                "state", state
        ));
    }
}
