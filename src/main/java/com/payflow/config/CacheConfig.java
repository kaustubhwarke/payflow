package com.payflow.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caching strategy (mentor feedback 22.f). A bounded, write-expiring Caffeine cache fronts the
 * hot, read-heavy user-by-UPI lookup. Entries are explicitly evicted on balance-changing writes
 * (see {@code TransactionServiceImpl}) so a cached balance is never served stale after a
 * transfer.
 */
@Configuration
public class CacheConfig {

    /** Cache of {@code upiId -> UserResponse} for the user-lookup hot path. */
    public static final String USERS_BY_UPI_ID = "usersByUpiId";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(USERS_BY_UPI_ID);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats());
        return cacheManager;
    }
}
