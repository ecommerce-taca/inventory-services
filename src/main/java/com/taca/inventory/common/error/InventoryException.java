package com.taca.inventory.common.error;

import java.util.Collections;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Domain exception carrying an {@link ErrorCode}, optional HTTP status override, and optional safe details.
 */
public class InventoryException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;
    private final Map<String, Object> details;

    public InventoryException(ErrorCode errorCode, String message) {
        this(errorCode, errorCode.getHttpStatus(), message, null, null);
    }

    public InventoryException(ErrorCode errorCode, String message, Map<String, Object> details) {
        this(errorCode, errorCode.getHttpStatus(), message, details, null);
    }

    public InventoryException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        this(errorCode, httpStatus, message, null, null);
    }

    public InventoryException(
            ErrorCode errorCode,
            HttpStatus httpStatus,
            String message,
            Map<String, Object> details
    ) {
        this(errorCode, httpStatus, message, details, null);
    }

    public InventoryException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, errorCode.getHttpStatus(), message, null, cause);
    }

    private InventoryException(
            ErrorCode errorCode,
            HttpStatus httpStatus,
            String message,
            Map<String, Object> details,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus != null ? httpStatus : errorCode.getHttpStatus();
        this.details = details == null ? null : Collections.unmodifiableMap(details);
    }

    /**
     * Returns the domain error code.
     *
     * @return error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the effective HTTP status for this exception.
     *
     * @return HTTP status
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * Returns optional non-sensitive error details, or {@code null}.
     *
     * @return details map or {@code null}
     */
    public Map<String, Object> getDetails() {
        return details;
    }
}
