package com.example.ticketmanager.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-process cache configuration using Caffeine.
 *
 * Cache names and their purpose:
 *   "usersByEmail"   – AppUser entities looked up by email address.
 *                      Short TTL so security-sensitive changes (disable, role update)
 *                      propagate within a reasonable time window.
 *                      Evicted explicitly by UserService on every mutation.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1_000));
        return manager;
    }
}

