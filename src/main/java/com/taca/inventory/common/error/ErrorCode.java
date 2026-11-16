package com.taca.inventory.common.error;

import org.springframework.http.HttpStatus;

/**
 * Canonical Inventory Service error codes and default HTTP status mappings.
 */
public enum ErrorCode {

    INVENTORY_INVALID_INPUT(HttpStatus.BAD_REQUEST),
    INVENTORY_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVENTORY_FORBIDDEN(HttpStatus.FORBIDDEN),
    INVENTORY_SKU_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVENTORY_SKU_DISABLED(HttpStatus.CONFLICT),
    INVENTORY_STOCK_INSUFFICIENT(HttpStatus.CONFLICT),
    INVENTORY_RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVENTORY_RESERVATION_STATE_INVALID(HttpStatus.CONFLICT),
    INVENTORY_RESERVATION_EXPIRED(HttpStatus.CONFLICT),
    INVENTORY_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    INVENTORY_CONCURRENCY_CONFLICT(HttpStatus.CONFLICT),
    INVENTORY_ADJUSTMENT_INVALID(HttpStatus.BAD_REQUEST),
    INVENTORY_DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INVENTORY_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    /**
     * Returns the default HTTP status associated with this error code.
     *
     * @return default HTTP status
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
