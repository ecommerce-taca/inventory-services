package com.taca.inventory.observability.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that initializes, propagates, and cleans up W3C trace context and {@code X-Request-ID}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        TraceContext context = TraceContext.parseOrCreate(
                request.getHeader(TraceContext.HEADER_TRACEPARENT),
                request.getHeader(TraceContext.HEADER_TRACESTATE),
                request.getHeader(TraceContext.HEADER_REQUEST_ID)
        );

        try {
            TraceContext.set(context);
            MDC.put("trace_id", context.traceId());
            MDC.put("span_id", context.spanId());
            MDC.put("request_id", context.requestId());

            response.setHeader(TraceContext.HEADER_TRACEPARENT, context.traceparent());
            if (context.tracestate() != null) {
                response.setHeader(TraceContext.HEADER_TRACESTATE, context.tracestate());
            }
            response.setHeader(TraceContext.HEADER_REQUEST_ID, context.requestId());

            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
            MDC.remove("trace_id");
            MDC.remove("span_id");
            MDC.remove("request_id");
        }
    }
}
