package com.payflow.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.config.PayflowProperties;
import com.payflow.exception.ErrorCode;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Token-bucket rate limiter (mentor feedback 22.e). Each client (identified by authenticated
 * subject, falling back to remote IP) gets an independent bucket; exceeding the quota yields a
 * structured HTTP 429 with a {@code Retry-After} header. Runs after authentication so the
 * subject is available, but before controllers.
 *
 * <p>The in-memory bucket store is appropriate for a single instance; in a multi-instance
 * deployment this would be backed by a distributed Bucket4j store (e.g. Redis) — documented as
 * a scaling step in the architecture docs.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final PayflowProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(PayflowProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.rateLimit().enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = buckets.computeIfAbsent(clientKey(request), key -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        writeTooManyRequests(response, Math.max(retryAfterSeconds, 1));
    }

    private Bucket newBucket() {
        PayflowProperties.RateLimit cfg = properties.rateLimit();
        Bandwidth limit = Bandwidth.builder()
                .capacity(cfg.capacity())
                .refillGreedy(cfg.capacity(), Duration.ofSeconds(cfg.refillPeriodSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String clientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return "sub:" + auth.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                ErrorCode.RATE_LIMIT_EXCEEDED.getStatus(), ErrorCode.RATE_LIMIT_EXCEEDED.getDefaultMessage());
        problem.setTitle(ErrorCode.RATE_LIMIT_EXCEEDED.getStatus().getReasonPhrase());
        problem.setProperty("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED.name());
        problem.setProperty("traceId", MDC.get(TracingFilter.TRACE_ID_KEY));

        response.setStatus(ErrorCode.RATE_LIMIT_EXCEEDED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
