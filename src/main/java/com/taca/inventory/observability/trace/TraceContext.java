package com.taca.inventory.observability.trace;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W3C trace context and correlation request identifier bound to the active execution thread.
 *
 * @param traceId 32-character lowercase hex trace identifier
 * @param spanId 16-character lowercase hex span identifier
 * @param traceFlags 2-character lowercase hex trace flags
 * @param tracestate optional W3C tracestate header
 * @param requestId correlation request identifier (max 64 chars)
 */
public record TraceContext(
        String traceId,
        String spanId,
        String traceFlags,
        String tracestate,
        String requestId
) {

    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String HEADER_TRACESTATE = "tracestate";
    public static final String HEADER_REQUEST_ID = "X-Request-ID";

    private static final ThreadLocal<TraceContext> HOLDER = new ThreadLocal<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\r\\n\\p{Cntrl}]");
    private static final Pattern TRACESTATE_PATTERN = Pattern.compile(
            "^[A-Za-z0-9_*/@-]+=[\\x20-\\x7e&&[^,=]]+(\\s*,\\s*[A-Za-z0-9_*/@-]+=[\\x20-\\x7e&&[^,=]]+)*$"
    );
    private static final String ZERO_TRACE_ID = "00000000000000000000000000000000";
    private static final String ZERO_SPAN_ID = "0000000000000000";
    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final int MAX_TRACESTATE_LENGTH = 512;

    /**
     * Parses incoming W3C trace headers and request ID, generating valid identifiers when absent or malformed.
     *
     * @param traceparent incoming {@code traceparent} header
     * @param tracestate incoming {@code tracestate} header
     * @param requestId incoming {@code X-Request-ID} header
     * @return valid {@link TraceContext}
     */
    public static TraceContext parseOrCreate(String traceparent, String tracestate, String requestId) {
        String resolvedTraceId = null;
        String resolvedFlags = "01";
        boolean validIncomingTraceparent = false;

        if (traceparent != null && !traceparent.isBlank()) {
            Matcher matcher = TRACEPARENT_PATTERN.matcher(traceparent.trim());
            if (matcher.matches()) {
                String candidateTraceId = matcher.group(1);
                String candidateParentSpanId = matcher.group(2);
                String candidateFlags = matcher.group(3);
                if (!ZERO_TRACE_ID.equals(candidateTraceId) && !ZERO_SPAN_ID.equals(candidateParentSpanId)) {
                    resolvedTraceId = candidateTraceId;
                    resolvedFlags = candidateFlags;
                    validIncomingTraceparent = true;
                }
            }
        }

        if (!validIncomingTraceparent) {
            resolvedTraceId = randomHex(16, ZERO_TRACE_ID);
        }
        String localSpanId = randomHex(8, ZERO_SPAN_ID);
        // Per W3C Trace Context §3.3.1, do not propagate tracestate when starting a new root trace.
        String resolvedTracestate = validIncomingTraceparent ? normalizeTracestate(tracestate) : null;
        String resolvedRequestId = normalizeRequestId(requestId);

        return new TraceContext(resolvedTraceId, localSpanId, resolvedFlags, resolvedTracestate, resolvedRequestId);
    }

    /**
     * Formats this context as a W3C {@code traceparent} header value.
     *
     * @return formatted {@code traceparent}
     */
    public String traceparent() {
        return "00-" + traceId + "-" + spanId + "-" + traceFlags;
    }

    /**
     * Returns the current thread's {@link TraceContext}, or {@code null} if unset.
     *
     * @return current trace context or {@code null}
     */
    public static TraceContext current() {
        return HOLDER.get();
    }

    /**
     * Binds the given {@link TraceContext} to the current thread.
     *
     * @param context trace context to bind
     */
    public static void set(TraceContext context) {
        if (context == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(context);
        }
    }

    /**
     * Clears the current thread's {@link TraceContext}.
     */
    public static void clear() {
        HOLDER.remove();
    }

    private static String normalizeRequestId(String requestId) {
        if (requestId == null) {
            return UUID.randomUUID().toString();
        }
        String sanitized = CONTROL_CHARS_PATTERN.matcher(requestId).replaceAll("").trim();
        if (sanitized.isBlank()) {
            return UUID.randomUUID().toString();
        }
        if (sanitized.length() > MAX_REQUEST_ID_LENGTH) {
            return sanitized.substring(0, MAX_REQUEST_ID_LENGTH);
        }
        return sanitized;
    }

    private static String normalizeTracestate(String tracestate) {
        if (tracestate == null) {
            return null;
        }
        String sanitized = CONTROL_CHARS_PATTERN.matcher(tracestate).replaceAll("").trim();
        if (sanitized.isBlank()
                || sanitized.length() > MAX_TRACESTATE_LENGTH
                || !TRACESTATE_PATTERN.matcher(sanitized).matches()) {
            return null;
        }
        return sanitized;
    }

    private static String randomHex(int numBytes, String forbiddenZero) {
        byte[] bytes = new byte[numBytes];
        String hex;
        do {
            RANDOM.nextBytes(bytes);
            hex = HEX.formatHex(bytes);
        } while (forbiddenZero.equals(hex));
        return hex;
    }
}
