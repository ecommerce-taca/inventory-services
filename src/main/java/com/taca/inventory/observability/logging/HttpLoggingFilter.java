package com.taca.inventory.observability.logging;

import com.taca.inventory.observability.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs completed HTTP requests as structured JSON without exposing sensitive headers or payloads.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

    private final JsonStructuredLogFormatter formatter;

    public HttpLoggingFilter(JsonStructuredLogFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
            String json = formatter.formatHttpAccess(
                    "INFO",
                    "http_request_completed",
                    TraceContext.current(),
                    request.getRequestURI(),
                    request.getMethod(),
                    response.getStatus(),
                    durationMs,
                    Map.of()
            );
            log.info("{}", json);
        }
    }
}
