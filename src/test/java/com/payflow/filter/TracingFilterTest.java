package com.payflow.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TracingFilter} (AAA pattern).
 */
class TracingFilterTest {

    private final TracingFilter filter = new TracingFilter();

    @Test
    void doFilter_generatesTraceAndSpanWhenNoHeaderPresent() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> traceDuringChain.set(MDC.get(TracingFilter.TRACE_ID_KEY));

        // Act
        filter.doFilter(request, response, chain);

        // Assert
        assertThat(response.getHeader(TracingFilter.TRACE_ID_HEADER)).hasSize(32);
        assertThat(response.getHeader(TracingFilter.SPAN_ID_HEADER)).hasSize(16);
        assertThat(traceDuringChain.get()).hasSize(32);
        // MDC must be cleared after the request completes
        assertThat(MDC.get(TracingFilter.TRACE_ID_KEY)).isNull();
        assertThat(MDC.get(TracingFilter.SPAN_ID_KEY)).isNull();
    }

    @Test
    void doFilter_honoursValidInboundTraceIdHeader() throws Exception {
        // Arrange
        String inbound = "0123456789abcdef0123456789abcdef";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TracingFilter.TRACE_ID_HEADER, inbound);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, (req, res) -> { });

        // Assert
        assertThat(response.getHeader(TracingFilter.TRACE_ID_HEADER)).isEqualTo(inbound);
    }

    @Test
    void doFilter_extractsTraceIdFromW3cTraceparentWhenExplicitHeaderInvalid() throws Exception {
        // Arrange
        String traceId = "abcdefabcdefabcdefabcdefabcdef01";
        String traceparent = "00-" + traceId + "-0123456789abcdef-01";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TracingFilter.TRACE_ID_HEADER, "not-valid");
        request.addHeader(TracingFilter.W3C_TRACEPARENT_HEADER, traceparent);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, (req, res) -> { });

        // Assert
        assertThat(response.getHeader(TracingFilter.TRACE_ID_HEADER)).isEqualTo(traceId);
    }

    @Test
    void doFilter_generatesFreshTraceWhenTraceparentMalformed() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TracingFilter.W3C_TRACEPARENT_HEADER, "garbage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, (req, res) -> { });

        // Assert
        assertThat(response.getHeader(TracingFilter.TRACE_ID_HEADER)).hasSize(32);
    }
}
