package com.helpagent.action.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for GitHub OAuth App authentication.
 * 
 * Properties are bound from application.yml under the "github.oauth" prefix.
 */
@Component
@ConfigurationProperties(prefix = "github.oauth")
public class GitHubOAuthProperties {

    /**
     * OAuth App Client ID from GitHub
     */
    private String clientId;

    /**
     * OAuth App Client Secret from GitHub
     */
    private String clientSecret;

    /**
     * The callback URL registered with GitHub OAuth App
     */
    private String redirectUri = "http://192.168.1.9:8080/oauth/callback";

    /**
     * Comma-separated list of OAuth scopes to request
     */
    private String scopes = "repo,user";

    // Getters and setters

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    /**
     * Checks if OAuth is properly configured with required credentials.
     */
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
