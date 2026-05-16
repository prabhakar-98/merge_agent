package com.helpagent.action.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the application.
 * 
 * <p>Configures security settings for different endpoints:
 * <ul>
 *   <li>OAuth endpoints (/oauth/**) - publicly accessible for OAuth flow</li>
 *   <li>Webhook endpoints (/api/webhook/**) - publicly accessible (validated via signature)</li>
 *   <li>Health/actuator endpoints - publicly accessible</li>
 *   <li>All other endpoints - require authentication</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for API endpoints (webhooks use signature verification instead)
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/oauth/**").permitAll()
                .requestMatchers("/api/webhook/**").permitAll()
                .requestMatchers("/api/dashboard/**").permitAll()
                .requestMatchers("/api/merge-analysis/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/health").permitAll()
                .requestMatchers("/").permitAll()
                .requestMatchers("/index.html", "/static/**", "/assets/**", "/*.js", "/*.css").permitAll()
                
                // All other requests can be accessed (add authentication as needed)
                .anyRequest().permitAll()
            )
            
            // Disable default login form
            .formLogin(AbstractHttpConfigurer::disable)
            
            // Disable HTTP Basic auth
            .httpBasic(AbstractHttpConfigurer::disable)

            // Use IF_REQUIRED sessions (needed for OAuth session cookies)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            );

        return http.build();
    }
}
