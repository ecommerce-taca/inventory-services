package com.taca.inventory.observability.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceContextFilterTest {

    private final TraceContextFilter filter = new TraceContextFilter();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        MDC.clear();
    }

    @Test
    void preservesValidW3cTraceparentAndRequestIdAndCleansUpAfterChain() throws Exception {
        String incomingTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/live");
        request.addHeader("traceparent", "00-" + incomingTraceId + "-00f067aa0ba902b7-01");
        request.addHeader("tracestate", "rojo=00f067aa0ba902b7");
        request.addHeader("X-Request-ID", "req-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<TraceContext> capturedContext = new AtomicReference<>();
        AtomicReference<String> capturedMdcTrace = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                capturedContext.set(TraceContext.current());
                capturedMdcTrace.set(MDC.get("trace_id"));
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(capturedContext.get()).isNotNull();
        assertThat(capturedContext.get().traceId()).isEqualTo(incomingTraceId);
        assertThat(capturedContext.get().spanId()).matches("^[0-9a-f]{16}$");
        assertThat(capturedContext.get().requestId()).isEqualTo("req-abc-123");
        assertThat(capturedMdcTrace.get()).isEqualTo(incomingTraceId);

        assertThat(response.getHeader("traceparent")).startsWith("00-" + incomingTraceId + "-");
        assertThat(response.getHeader("tracestate")).isEqualTo("rojo=00f067aa0ba902b7");
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("req-abc-123");

        assertThat(TraceContext.current()).isNull();
        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("request_id")).isNull();
    }

    @Test
    void generatesTraceAndTruncatesOversizedRequestIdWhenTraceparentMissingOrMalformed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/ready");
        request.addHeader("traceparent", "malformed-traceparent");
        String longRequestId = "r".repeat(90);
        request.addHeader("X-Request-ID", longRequestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String responseTraceparent = response.getHeader("traceparent");
        assertThat(responseTraceparent).matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$");
        assertThat(response.getHeader("X-Request-ID")).hasSize(64);
        assertThat(TraceContext.current()).isNull();
    }

    @Test
    void generatesUuidRequestIdWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/live");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-ID")).isNotBlank();
        assertThat(response.getHeader("X-Request-ID").length()).isLessThanOrEqualTo(64);
        assertThat(response.getHeader("traceparent")).matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$");
    }

    @Test
    void clearsTraceContextAndMdcEvenWhenResponseSetHeaderThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/live");
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        request.addHeader("X-Request-ID", "req-throw-test");
        HttpServletResponse response = mock(HttpServletResponse.class);
        doThrow(new IllegalStateException("header failure")).when(response).setHeader(anyString(), anyString());

        assertThatThrownBy(() -> filter.doFilter(request, response, new MockFilterChain()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("header failure");

        assertThat(TraceContext.current()).isNull();
        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("span_id")).isNull();
        assertThat(MDC.get("request_id")).isNull();
    }

    @Test
    void stripsControlCharactersFromRequestIdAndTracestateBeforeSettingHeadersAndMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/live");
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        request.addHeader("tracestate", "rojo=00f067aa0ba902b7\r\n\u0000");
        request.addHeader("X-Request-ID", "req-123\r\nX-Injected:evil\t\u0007");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedMdcRequestId = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                capturedMdcRequestId.set(MDC.get("request_id"));
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(capturedMdcRequestId.get()).isEqualTo("req-123X-Injected:evil");
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("req-123X-Injected:evil");
        assertThat(response.getHeader("tracestate")).isEqualTo("rojo=00f067aa0ba902b7");
    }

    @Test
    void clearsIncomingTracestateWhenTraceparentIsMissingOrInvalid() throws Exception {
        MockHttpServletRequest missingParentRequest = new MockHttpServletRequest("GET", "/health/live");
        missingParentRequest.addHeader("tracestate", "rojo=00f067aa0ba902b7");
        MockHttpServletResponse missingParentResponse = new MockHttpServletResponse();
        AtomicReference<TraceContext> missingParentContext = new AtomicReference<>();

        filter.doFilter(missingParentRequest, missingParentResponse, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                missingParentContext.set(TraceContext.current());
            }
        });

        assertThat(missingParentContext.get().tracestate()).isNull();
        assertThat(missingParentResponse.getHeader("tracestate")).isNull();

        MockHttpServletRequest invalidParentRequest = new MockHttpServletRequest("GET", "/health/live");
        invalidParentRequest.addHeader("traceparent", "00-00000000000000000000000000000000-00f067aa0ba902b7-01");
        invalidParentRequest.addHeader("tracestate", "rojo=00f067aa0ba902b7");
        MockHttpServletResponse invalidParentResponse = new MockHttpServletResponse();
        AtomicReference<TraceContext> invalidParentContext = new AtomicReference<>();

        filter.doFilter(invalidParentRequest, invalidParentResponse, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                invalidParentContext.set(TraceContext.current());
            }
        });

        assertThat(invalidParentContext.get().tracestate()).isNull();
        assertThat(invalidParentResponse.getHeader("tracestate")).isNull();
    }
}
