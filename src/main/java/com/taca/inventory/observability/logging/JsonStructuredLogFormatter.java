package com.taca.inventory.observability.logging;

import com.taca.inventory.observability.trace.TraceContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Formats single-line JSON structured logs conforming to the platform observability schema.
 */
@Component
public class JsonStructuredLogFormatter {

    private final ObjectMapper objectMapper;
    private final SensitiveDataMasker masker;
    private final String service;
    private final String env;
    private final String version;

    @Autowired
    public JsonStructuredLogFormatter(
            ObjectMapper objectMapper,
            SensitiveDataMasker masker,
            @Value("${spring.application.name:inventory-service}") String service,
            @Value("${inventory.observability.env:${SPRING_PROFILES_ACTIVE:local}}") String env,
            @Value("${inventory.observability.version:0.0.1-SNAPSHOT}") String version
    ) {
        this.objectMapper = objectMapper;
        this.masker = masker;
        this.service = service;
        this.env = env;
        this.version = version;
    }

    /**
     * Formats an HTTP access log entry as single-line JSON with masked extra fields.
     *
     * @param level log level (e.g. {@code INFO}, {@code WARN}, {@code ERROR})
     * @param event event identifier (e.g. {@code http_request_completed})
     * @param trace active trace context
     * @param route request URI
     * @param method HTTP method
     * @param statusCode HTTP response status code
     * @param durationMs request duration in milliseconds
     * @param extraFields optional allowlisted metadata fields
     * @return single-line JSON string
     */
    public String formatHttpAccess(
            String level,
            String event,
            TraceContext trace,
            String route,
            String method,
            int statusCode,
            long durationMs,
            Map<String, Object> extraFields
    ) {
        Map<String, Object> payload = baseFields(level, event, trace);
        payload.put("route", route);
        payload.put("method", method);
        payload.put("status_code", statusCode);
        payload.put("duration_ms", durationMs);
        appendSafeExtras(payload, extraFields);
        return serializeSingleLine(payload);
    }

    /**
     * Formats a general domain or infrastructure structured log event as single-line JSON.
     *
     * @param level log level
     * @param event event name
     * @param trace active trace context
     * @param extraFields optional metadata fields (masked)
     * @return single-line JSON string
     */
    public String formatEvent(
            String level,
            String event,
            TraceContext trace,
            Map<String, Object> extraFields
    ) {
        Map<String, Object> payload = baseFields(level, event, trace);
        appendSafeExtras(payload, extraFields);
        return serializeSingleLine(payload);
    }

    private Map<String, Object> baseFields(String level, String event, TraceContext trace) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("level", level);
        payload.put("service", service);
        payload.put("env", env);
        payload.put("version", version);
        payload.put("event", event);
        if (trace != null) {
            payload.put("trace_id", trace.traceId());
            payload.put("span_id", trace.spanId());
            payload.put("request_id", trace.requestId());
        }
        return payload;
    }

    private void appendSafeExtras(Map<String, Object> target, Map<String, Object> extraFields) {
        if (extraFields == null || extraFields.isEmpty()) {
            return;
        }
        Map<String, Object> masked = masker.maskMap(extraFields);
        for (Map.Entry<String, Object> entry : masked.entrySet()) {
            target.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private String serializeSingleLine(Map<String, Object> payload) {
        String json = objectMapper.writeValueAsString(payload);
        return json.replace("\n", "").replace("\r", "");
    }
}
