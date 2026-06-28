package com.payflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Supplies the current principal name to JPA auditing. Although the entities currently audit
 * only timestamps, exposing an {@link AuditorAware} keeps the system ready to populate
 * {@code @CreatedBy}/{@code @LastModifiedBy} columns without further wiring.
 */
@Configuration
public class AuditingConfig {

    static final String SYSTEM_ACTOR = "system";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of(SYSTEM_ACTOR);
            }
            return Optional.ofNullable(authentication.getName());
        };
    }
}
