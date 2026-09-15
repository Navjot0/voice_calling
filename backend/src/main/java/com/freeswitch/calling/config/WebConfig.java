package com.freeswitch.calling.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enables CORS for the voice API so the frontend - a separate React/Vite
 * app running on its own origin (a different port in local development, a
 * different host/port entirely once deployed) - can call it from a browser.
 *
 * <p>Scoped narrowly to {@code /api/v1/voice/**} and only to the origins
 * configured in {@code app.cors.allowed-origins} (see {@link CorsProperties})
 * - never a wildcard, and never solved by disabling the browser's own CORS
 * checks on the frontend side.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public WebConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/voice/**")
                .allowedOrigins(corsProperties.getAllowedOrigins().toArray(new String[0]))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type")
                .maxAge(3600);
    }
}
