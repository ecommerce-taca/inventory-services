package com.taca.inventory.common.error;

import com.taca.inventory.observability.logging.SensitiveDataMasker;
import com.taca.inventory.observability.trace.TraceContext;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates domain, validation, request-binding, and unexpected exceptions into {@link ErrorEnvelope}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SensitiveDataMasker masker;

    public GlobalExceptionHandler(SensitiveDataMasker masker) {
        this.masker = masker;
    }

    @ExceptionHandler(InventoryException.class)
    public ResponseEntity<ErrorEnvelope> handleInventoryException(InventoryException ex) {
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ErrorEnvelope.of(ex.getErrorCode(), ex.getMessage(), ex.getDetails(), resolveTraceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return badRequest("Validation failed", fieldErrors);
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ErrorEnvelope> handleValidationException(Exception ex) {
        return badRequest("Validation failed", null);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            ConversionFailedException.class
    })
    public ResponseEntity<ErrorEnvelope> handleBadInput(Exception ex) {
        return badRequest("Invalid request input", null);
    }

    @ExceptionHandler({
            NoResourceFoundException.class,
            NoHandlerFoundException.class
    })
    public ResponseEntity<ErrorEnvelope> handleNotFound(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorEnvelope.of("NOT_FOUND", "Resource not found", null, resolveTraceId()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorEnvelope.of("METHOD_NOT_ALLOWED", "HTTP method not supported", null, resolveTraceId()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorEnvelope.of("UNSUPPORTED_MEDIA_TYPE", "Unsupported media type", null, resolveTraceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception ex) {
        String traceId = resolveTraceId();
        String maskedMessage = masker.mask(ex.getMessage());
        log.error("Unhandled inventory exception type={} trace_id={} message={}",
                ex.getClass().getSimpleName(), traceId, maskedMessage);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorEnvelope.of(ErrorCode.INVENTORY_INTERNAL_ERROR, "Internal server error", null, traceId));
    }

    private ResponseEntity<ErrorEnvelope> badRequest(String message, Map<String, Object> details) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorEnvelope.of(ErrorCode.INVENTORY_INVALID_INPUT, message, details, resolveTraceId()));
    }

    private String resolveTraceId() {
        TraceContext current = TraceContext.current();
        if (current != null) {
            return current.traceId();
        }
        return MDC.get("trace_id");
    }
}
