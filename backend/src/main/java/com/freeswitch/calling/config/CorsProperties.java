package com.freeswitch.calling.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the {@code app.cors.*} configuration block, controlling which
 * browser origins may call this API's {@code /api/v1/voice/**} endpoints
 * (see {@link WebConfig}). The frontend is a separate React/Vite app served
 * from its own origin, so without this the browser blocks its requests.
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Origins allowed to call the API from a browser - scheme + host + port,
     * no path, no trailing slash (e.g. {@code http://localhost:5173}, or wildcard patterns
     * like {@code *} or {@code http://192.168.*:*}).
     * Defaults to {@code *} to allow local development and LAN browser access.
     * Override with the {@code CORS_ALLOWED_ORIGINS} environment variable (comma-separated).
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("*"));

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
