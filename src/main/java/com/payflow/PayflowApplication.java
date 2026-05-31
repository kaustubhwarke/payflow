package com.payflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * PayFlow application entry point.
 *
 * <p>PayFlow is a production-ready, UPI-style payment microservice. This class boots the
 * embedded servlet container (Spring Boot feature #1), triggers component scanning and
 * auto-configuration (feature #2), and wires the production-ready Actuator endpoints
 * (feature #3).</p>
 *
 * <ul>
 *     <li>{@link EnableJpaAuditing} &mdash; populates {@code createdAt}/{@code updatedAt}
 *     auditing columns automatically on persist/update.</li>
 *     <li>{@link EnableAsync} &mdash; enables the {@code @Async} executor used for
 *     non-blocking event publication.</li>
 *     <li>{@link EnableCaching} &mdash; activates the Caffeine-backed cache abstraction.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableAsync
@EnableCaching
public class PayflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayflowApplication.class, args);
    }
}
