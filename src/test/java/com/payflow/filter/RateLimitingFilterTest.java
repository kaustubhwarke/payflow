package com.payflow.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.config.PayflowProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RateLimitingFilter} (AAA pattern).
 */
class RateLimitingFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RateLimitingFilter filter(boolean enabled, int capacity) {
        PayflowProperties props = new PayflowProperties(
                new PayflowProperties.RateLimit(enabled, capacity, 60),
                new PayflowProperties.Tracing(32, 16),
                new PayflowProperties.Security("http://localhost"));
        return new RateLimitingFilter(props, objectMapper);
    }

    private MockHttpServletRequest apiRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/users");
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotFilter_whenDisabled() {
        assertThat(filter(false, 10).shouldNotFilter(apiRequest())).isTrue();
    }

    @Test
    void shouldNotFilter_whenPathIsNotApi() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/swagger-ui.html");
        assertThat(filter(true, 10).shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldFilter_whenEnabledAndApiPath() {
        assertThat(filter(true, 10).shouldNotFilter(apiRequest())).isFalse();
    }

    @Test
    void doFilter_allowsRequestWithinQuotaAndSetsRemainingHeader() throws Exception {
        // Arrange
        RateLimitingFilter filter = filter(true, 5);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();
        FilterChain chain = (req, res) -> chainCalls.incrementAndGet();

        // Act
        filter.doFilter(apiRequest(), response, chain);

        // Assert
        assertThat(chainCalls.get()).isEqualTo(1);
        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isEqualTo("4");
    }

    @Test
    void doFilter_returns429WhenQuotaExhausted() throws Exception {
        // Arrange: capacity of 1 -> the second call from the same client is throttled
        RateLimitingFilter filter = filter(true, 1);
        FilterChain chain = (req, res) -> { };

        // Act: first request consumes the only token
        filter.doFilter(apiRequest(), new MockHttpServletResponse(), chain);
        MockHttpServletResponse throttled = new MockHttpServletResponse();
        filter.doFilter(apiRequest(), throttled, chain);

        // Assert
        assertThat(throttled.getStatus()).isEqualTo(429);
        assertThat(throttled.getHeader("Retry-After")).isNotNull();
        assertThat(throttled.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void doFilter_keysByAuthenticatedSubjectWhenPresent() throws Exception {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "n/a",
                        AuthorityUtils.createAuthorityList("ROLE_USER")));
        RateLimitingFilter filter = filter(true, 5);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        // Act
        filter.doFilter(apiRequest(), response, (req, res) -> chainCalls.incrementAndGet());

        // Assert
        assertThat(chainCalls.get()).isEqualTo(1);
        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isEqualTo("4");
    }
}
