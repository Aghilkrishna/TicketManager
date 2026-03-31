package com.example.ticketmanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        String baseUrl,
        Mail mail,
        String uploadDir
) {
    public record Jwt(String secret, long expiration) {
    }

    public record Mail(boolean enabled, String fromAddress, String fromName) {
    }
}
