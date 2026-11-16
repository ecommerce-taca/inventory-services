package com.taca.inventory.observability.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KafkaTracePropagatorTest {

    private final KafkaTracePropagator propagator = new KafkaTracePropagator();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void injectsAndExtractsTraceparentTracestateAndRequestId() {
        TraceContext source = TraceContext.parseOrCreate(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "congo=t61rcWkgMzE",
                "req-kafka-42"
        );
        Headers headers = new RecordHeaders();

        propagator.inject(headers, source);

        assertThat(new String(headers.lastHeader("traceparent").value(), StandardCharsets.UTF_8))
                .startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
        assertThat(new String(headers.lastHeader("tracestate").value(), StandardCharsets.UTF_8))
                .isEqualTo("congo=t61rcWkgMzE");
        assertThat(new String(headers.lastHeader("X-Request-ID").value(), StandardCharsets.UTF_8))
                .isEqualTo("req-kafka-42");

        TraceContext extracted = propagator.extract(headers);
        assertThat(extracted.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(extracted.tracestate()).isEqualTo("congo=t61rcWkgMzE");
        assertThat(extracted.requestId()).isEqualTo("req-kafka-42");
    }

    @Test
    void extractGeneratesValidTraceContextWhenHeadersAreMissing() {
        TraceContext extracted = propagator.extract(new RecordHeaders());

        assertThat(extracted.traceId()).matches("^[0-9a-f]{32}$").isNotEqualTo("00000000000000000000000000000000");
        assertThat(extracted.spanId()).matches("^[0-9a-f]{16}$").isNotEqualTo("0000000000000000");
        assertThat(extracted.requestId()).isNotBlank();
    }

    @Test
    void injectRemovesStaleTracestateHeaderWhenContextTracestateIsNullOrBlank() {
        Headers headersWithNullState = new RecordHeaders();
        headersWithNullState.add("tracestate", "stale=value".getBytes(StandardCharsets.UTF_8));
        TraceContext nullStateContext = new TraceContext(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                "01",
                null,
                "req-null-state"
        );

        propagator.inject(headersWithNullState, nullStateContext);

        assertThat(headersWithNullState.lastHeader("tracestate")).isNull();
        assertThat(new String(headersWithNullState.lastHeader("traceparent").value(), StandardCharsets.UTF_8))
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(new String(headersWithNullState.lastHeader("X-Request-ID").value(), StandardCharsets.UTF_8))
                .isEqualTo("req-null-state");

        Headers headersWithBlankState = new RecordHeaders();
        headersWithBlankState.add("tracestate", "stale=value".getBytes(StandardCharsets.UTF_8));
        TraceContext blankStateContext = new TraceContext(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                "01",
                "   ",
                "req-blank-state"
        );

        propagator.inject(headersWithBlankState, blankStateContext);

        assertThat(headersWithBlankState.lastHeader("tracestate")).isNull();
    }
}
