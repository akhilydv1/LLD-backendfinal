package com.designloop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public WebConfig(@Value("${designloop.cors.allowed-origins:http://localhost:5173,http://localhost:5174}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split(",");
        for (int i = 0; i < this.allowedOrigins.length; i++) this.allowedOrigins[i] = this.allowedOrigins[i].trim();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Learner-Id", "Idempotency-Key", "Accept")
                .exposedHeaders("Location", "X-Request-Id")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
