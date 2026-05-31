package com.payflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed, validated binding of the {@code payflow.*} configuration tree. Centralising tunables
 * here keeps magic numbers out of the code and makes them environment-overridable.
 *
 * @param rateLimit rate-limiting tunables
 * @param tracing   trace/span identifier lengths
 * @param security  security tunables (CORS)
 */
@ConfigurationProperties(prefix = "payflow")
public record PayflowProperties(RateLimit rateLimit, Tracing tracing, Security security) {

    /**
     * @param enabled            whether rate limiting is active
     * @param capacity           number of tokens (requests) per window
     * @param refillPeriodSeconds window length in seconds
     */
    public record RateLimit(boolean enabled, int capacity, int refillPeriodSeconds) {
    }

    /**
     * @param traceIdLength expected trace-id length in hex chars (32 = W3C standard)
     * @param spanIdLength  expected span-id length in hex chars (16 = W3C standard)
     */
    public record Tracing(int traceIdLength, int spanIdLength) {
    }

    /**
     * @param corsAllowedOrigins comma-separated list of permitted CORS origins
     */
    public record Security(String corsAllowedOrigins) {
    }
}
