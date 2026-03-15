package com.helpagent.action.tools;

/**
 * Thread-local storage for OAuth context during agent execution.
 * This allows tools to access OAuth tokens without modifying their signatures.
 */
public class OAuthContext {
    private static final ThreadLocal<String> oauthUserId = new ThreadLocal<>();

    /**
     * Sets the OAuth user ID for the current thread.
     */
    public static void setOAuthUserId(String userId) {
        oauthUserId.set(userId);
    }

    /**
     * Gets the OAuth user ID for the current thread.
     */
    public static String getOAuthUserId() {
        return oauthUserId.get();
    }

    /**
     * Clears the OAuth context for the current thread.
     */
    public static void clear() {
        oauthUserId.remove();
    }
}
