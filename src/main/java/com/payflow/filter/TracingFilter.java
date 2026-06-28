package com.payflow.filter;

import com.payflow.util.TraceIdentifierFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Establishes a distributed-tracing context for every request (Rule 13, mentor feedback 22.c).
 *
 * <p>For each request it derives a <b>traceId</b> (32 hex chars / 128-bit) &mdash; honouring an
 * inbound W3C {@code traceparent} or {@code X-Trace-Id} header when present and valid, otherwise
 * generating a fresh one &mdash; and always mints a new <b>spanId</b> (16 hex chars / 64-bit).
 * Both are placed in the SLF4J {@link MDC} so every log line is correlated, and echoed back as
 * response headers so callers can correlate client-side.</p>
 *
 * <p>Runs first in the chain so even security failures are traced; always clears the MDC in a
 * {@code finally} block to prevent context bleeding across pooled threads.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TracingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY = "spanId";

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SPAN_ID_HEADER = "X-Span-Id";
    public static final String W3C_TRACEPARENT_HEADER = "traceparent";

    private static final int TRACE_ID_LENGTH = 32;
    private static final int SPAN_ID_LENGTH = 16;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        String spanId = TraceIdentifierFactory.newSpanId();

        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put(SPAN_ID_KEY, spanId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(SPAN_ID_HEADER, spanId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(SPAN_ID_KEY);
        }
    }

    /**
     * Extracts a propagated trace ID, preferring an explicit {@code X-Trace-Id}, then the
     * trace-id segment of a W3C {@code traceparent} ({@code version-traceId-spanId-flags}).
     * Falls back to a freshly generated value if none is valid.
     */
    private String resolveTraceId(HttpServletRequest request) {
        String explicit = request.getHeader(TRACE_ID_HEADER);
        if (TraceIdentifierFactory.isValid(explicit, TRACE_ID_LENGTH)) {
            return explicit;
        }
        String traceparent = request.getHeader(W3C_TRACEPARENT_HEADER);
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 2 && TraceIdentifierFactory.isValid(parts[1], TRACE_ID_LENGTH)) {
                return parts[1];
            }
        }
        return TraceIdentifierFactory.newTraceId();
    }
}
