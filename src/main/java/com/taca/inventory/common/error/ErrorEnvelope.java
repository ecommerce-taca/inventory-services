package com.taca.inventory.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Standard API error envelope {@code { "error": { "code", "message", "details", "trace_id" } }}.
 *
 * @param error error body
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(ErrorBody error) {

    /**
     * Builds an error envelope omitting null optional fields.
     *
     * @param code domain error code
     * @param message safe human-readable message
     * @param details optional structured context
     * @param traceId active W3C trace identifier
     * @return wrapped error envelope
     */
    public static ErrorEnvelope of(
            ErrorCode code,
            String message,
            Map<String, Object> details,
            String traceId
    ) {
        return of(code.name(), message, details, traceId);
    }

    /**
     * Builds an error envelope omitting null optional fields for a raw error code string.
     *
     * @param code error code string
     * @param message safe human-readable message
     * @param details optional structured context
     * @param traceId active W3C trace identifier
     * @return wrapped error envelope
     */
    public static ErrorEnvelope of(
            String code,
            String message,
            Map<String, Object> details,
            String traceId
    ) {
        Map<String, Object> safeDetails = (details == null || details.isEmpty()) ? null : details;
        return new ErrorEnvelope(new ErrorBody(code, message, safeDetails, traceId));
    }

    /**
     * Inner error payload.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(
            String code,
            String message,
            Map<String, Object> details,
            @JsonProperty("trace_id") String traceId
    ) {
    }
}
