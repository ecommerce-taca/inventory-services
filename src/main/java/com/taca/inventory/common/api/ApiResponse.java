package com.taca.inventory.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard API response envelope {@code { "data": ..., "meta": { ... } }}.
 *
 * @param data response payload
 * @param meta response metadata
 * @param <T> payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        T data,
        Meta meta
) {

    /**
     * Creates a single-resource or command response envelope.
     *
     * @param data response payload
     * @param requestId request identifier
     * @param <T> payload type
     * @return wrapped API response
     */
    public static <T> ApiResponse<T> of(T data, String requestId) {
        return new ApiResponse<>(data, new Meta(requestId, null, null, null, null));
    }

    /**
     * Creates a paginated list response envelope.
     *
     * @param data response page items
     * @param requestId request identifier
     * @param page 1-based page number
     * @param size page size
     * @param total total matching items
     * @param totalPages total available pages
     * @param <T> payload type
     * @return wrapped paginated API response
     */
    public static <T> ApiResponse<T> paginated(
            T data,
            String requestId,
            int page,
            int size,
            long total,
            int totalPages
    ) {
        return new ApiResponse<>(data, new Meta(requestId, page, size, total, totalPages));
    }

    /**
     * Response metadata block with optional pagination fields.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(
            @JsonProperty("request_id") String requestId,
            Integer page,
            Integer size,
            Long total,
            @JsonProperty("total_pages") Integer totalPages
    ) {
    }
}
