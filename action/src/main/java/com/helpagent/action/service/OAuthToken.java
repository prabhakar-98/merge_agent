package com.helpagent.action.service;

import java.time.Instant;

/**
 * Represents an OAuth 2.0 access token obtained from GitHub.
 * 
 * <p>GitHub OAuth tokens include:
 * <ul>
 *   <li><b>accessToken</b> — The token used for API authentication</li>
 *   <li><b>tokenType</b> — Usually "bearer"</li>
 *   <li><b>scope</b> — Comma-separated list of granted scopes</li>
 *   <li><b>refreshToken</b> — Optional token for refreshing (if enabled in GitHub App)</li>
 *   <li><b>expiresAt</b> — When the token expires (null for non-expiring tokens)</li>
 * </ul>
 * 
 * @see <a href="https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps">GitHub OAuth Apps</a>
 */
public record OAuthToken(
        String accessToken,
        String tokenType,
        String scope,
        String refreshToken,
        Instant expiresAt
) {
    /**
     * Creates an OAuthToken from GitHub's token response.
     * 
     * @param accessToken The access token
     * @param tokenType The token type (usually "bearer")
     * @param scope Comma-separated scopes
     * @return A new OAuthToken instance
     */
    public static OAuthToken of(String accessToken, String tokenType, String scope) {
        return new OAuthToken(accessToken, tokenType, scope, null, null);
    }

    /**
     * Creates an OAuthToken with expiration (for GitHub Apps with token expiration enabled).
     */
    public static OAuthToken withExpiration(String accessToken, String tokenType, String scope,
                                            String refreshToken, int expiresInSeconds) {
        Instant expiry = expiresInSeconds > 0 
                ? Instant.now().plusSeconds(expiresInSeconds) 
                : null;
        return new OAuthToken(accessToken, tokenType, scope, refreshToken, expiry);
    }

    /**
     * Checks if the token has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the token will expire within the given number of seconds.
     */
    public boolean expiresWithin(int seconds) {
        return expiresAt != null && Instant.now().plusSeconds(seconds).isAfter(expiresAt);
    }

    /**
     * Checks if this token has a specific scope.
     */
    public boolean hasScope(String requiredScope) {
        if (scope == null || scope.isBlank()) {
            return false;
        }
        String[] scopes = scope.split(",");
        for (String s : scopes) {
            if (s.trim().equals(requiredScope)) {
                return true;
            }
        }
        return false;
    }
}
