package com.taca.inventory.observability.trace;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.stereotype.Component;

/**
 * Injects and extracts W3C {@code traceparent}/{@code tracestate} and {@code X-Request-ID} across Kafka headers.
 */
@Component
public class KafkaTracePropagator {

    /**
     * Injects the given {@link TraceContext} into Kafka record headers.
     *
     * @param headers target Kafka headers
     * @param context trace context to inject (if {@code null}, uses active thread context or creates a new one)
     */
    public void inject(Headers headers, TraceContext context) {
        if (headers == null) {
            return;
        }
        TraceContext effective = context != null ? context : TraceContext.current();
        if (effective == null) {
            effective = TraceContext.parseOrCreate(null, null, null);
        }

        putHeader(headers, TraceContext.HEADER_TRACEPARENT, effective.traceparent());
        if (effective.tracestate() != null && !effective.tracestate().isBlank()) {
            putHeader(headers, TraceContext.HEADER_TRACESTATE, effective.tracestate());
        } else {
            headers.remove(TraceContext.HEADER_TRACESTATE);
        }
        putHeader(headers, TraceContext.HEADER_REQUEST_ID, effective.requestId());
    }

    /**
     * Extracts a {@link TraceContext} from Kafka record headers, generating valid identifiers when absent.
     *
     * @param headers source Kafka headers
     * @return extracted or newly generated {@link TraceContext}
     */
    public TraceContext extract(Headers headers) {
        String traceparent = readHeader(headers, TraceContext.HEADER_TRACEPARENT);
        String tracestate = readHeader(headers, TraceContext.HEADER_TRACESTATE);
        String requestId = readHeader(headers, TraceContext.HEADER_REQUEST_ID);
        return TraceContext.parseOrCreate(traceparent, tracestate, requestId);
    }

    private static void putHeader(Headers headers, String key, String value) {
        headers.remove(key);
        headers.add(key, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String readHeader(Headers headers, String key) {
        if (headers == null) {
            return null;
        }
        Header header = headers.lastHeader(key);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
