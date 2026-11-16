package com.taca.inventory.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.taca.inventory.observability.trace.TraceContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class JsonStructuredLogFormatterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final SensitiveDataMasker masker = new SensitiveDataMasker();
    private final JsonStructuredLogFormatter formatter = new JsonStructuredLogFormatter(
            objectMapper, masker, "inventory-service", "test", "0.0.1-SNAPSHOT"
    );

    @Test
    void formatsHttpAccessLogWithAllRequiredJsonFieldsAndMaskedSecrets() {
        TraceContext trace = TraceContext.parseOrCreate(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                null,
                "req-log-01"
        );

        String json = formatter.formatHttpAccess(
                "INFO",
                "http_request_completed",
                trace,
                "/internal/inventory/availability",
                "GET",
                200,
                14L,
                Map.of(
                        "authorization", "Bearer secret-jwt",
                        "email", "secret@example.com",
                        "reservation_id", "res-123"
                )
        );

        assertThat(json).doesNotContain("\n");
        assertThat(json).doesNotContain("secret-jwt");
        assertThat(json).doesNotContain("secret@example.com");

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("timestamp").asString()).isNotBlank();
        assertThat(node.get("level").asString()).isEqualTo("INFO");
        assertThat(node.get("service").asString()).isEqualTo("inventory-service");
        assertThat(node.get("env").asString()).isEqualTo("test");
        assertThat(node.get("version").asString()).isEqualTo("0.0.1-SNAPSHOT");
        assertThat(node.get("event").asString()).isEqualTo("http_request_completed");
        assertThat(node.get("trace_id").asString()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(node.get("span_id").asString()).hasSize(16);
        assertThat(node.get("request_id").asString()).isEqualTo("req-log-01");
        assertThat(node.get("route").asString()).isEqualTo("/internal/inventory/availability");
        assertThat(node.get("method").asString()).isEqualTo("GET");
        assertThat(node.get("status_code").asInt()).isEqualTo(200);
        assertThat(node.get("duration_ms").asLong()).isEqualTo(14L);
        assertThat(node.get("reservation_id").asString()).isEqualTo("res-123");
        assertThat(node.get("authorization").asString()).isEqualTo("[REDACTED]");
        assertThat(node.get("email").asString()).isEqualTo("[REDACTED]");
    }
}
