package com.taca.inventory.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taca.inventory.common.api.ApiResponse;
import com.taca.inventory.observability.logging.HttpLoggingFilter;
import com.taca.inventory.observability.logging.JsonStructuredLogFormatter;
import com.taca.inventory.observability.logging.SensitiveDataMasker;
import com.taca.inventory.observability.trace.TraceContextFilter;
import com.taca.inventory.security.ActorContextFilter;
import com.taca.inventory.security.SecurityProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.StubErrorController.class)
@Import({
        GlobalExceptionHandlerTest.StubErrorController.class,
        GlobalExceptionHandler.class,
        TraceContextFilter.class,
        ActorContextFilter.class,
        HttpLoggingFilter.class,
        JsonStructuredLogFormatter.class,
        SensitiveDataMasker.class
})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "inventory.security.internal-token=test-internal-token")
class GlobalExceptionHandlerTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void errorCodeEnumDefinesAllFourteenExpectedCodesAndDefaultStatuses() {
        assertThat(ErrorCode.values()).hasSize(14);
        assertThat(ErrorCode.INVENTORY_INVALID_INPUT.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.INVENTORY_UNAUTHENTICATED.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.INVENTORY_FORBIDDEN.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.INVENTORY_SKU_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.INVENTORY_SKU_DISABLED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVENTORY_STOCK_INSUFFICIENT.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVENTORY_RESERVATION_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.INVENTORY_RESERVATION_STATE_INVALID.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVENTORY_RESERVATION_EXPIRED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVENTORY_IDEMPOTENCY_CONFLICT.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVENTORY_CONCURRENCY_CONFLICT.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVENTORY_ADJUSTMENT_INVALID.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.INVENTORY_DEPENDENCY_UNAVAILABLE.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ErrorCode.INVENTORY_INTERNAL_ERROR.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void envelopesOmitNullFieldsDuringSerialization() {
        ErrorEnvelope envelope = ErrorEnvelope.of(ErrorCode.INVENTORY_SKU_NOT_FOUND, "SKU missing", null, TRACE_ID);
        JsonNode errorTree = objectMapper.valueToTree(envelope);
        assertThat(errorTree.get("error").has("details")).isFalse();
        assertThat(errorTree.get("error").get("code").asString()).isEqualTo("INVENTORY_SKU_NOT_FOUND");
        assertThat(errorTree.get("error").get("trace_id").asString()).isEqualTo(TRACE_ID);

        ApiResponse<String> single = ApiResponse.of("ok", "req-1");
        JsonNode singleTree = objectMapper.valueToTree(single);
        assertThat(singleTree.get("data").asString()).isEqualTo("ok");
        assertThat(singleTree.get("meta").get("request_id").asString()).isEqualTo("req-1");
        assertThat(singleTree.get("meta").has("page")).isFalse();
        assertThat(singleTree.get("meta").has("size")).isFalse();
        assertThat(singleTree.get("meta").has("total")).isFalse();
        assertThat(singleTree.get("meta").has("total_pages")).isFalse();

        ApiResponse<String> paged = ApiResponse.paginated("items", "req-2", 1, 20, 42L, 3);
        JsonNode pagedTree = objectMapper.valueToTree(paged);
        assertThat(pagedTree.get("meta").get("page").asInt()).isEqualTo(1);
        assertThat(pagedTree.get("meta").get("size").asInt()).isEqualTo(20);
        assertThat(pagedTree.get("meta").get("total").asLong()).isEqualTo(42L);
        assertThat(pagedTree.get("meta").get("total_pages").asInt()).isEqualTo(3);
    }

    @Test
    void inventoryExceptionUsesDefaultStatusAndDetailsWithTraceId() throws Exception {
        mockMvc.perform(get("/test/errors/insufficient")
                        .header("traceparent", TRACEPARENT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVENTORY_STOCK_INSUFFICIENT"))
                .andExpect(jsonPath("$.error.message").value("Not enough available stock"))
                .andExpect(jsonPath("$.error.details.available").value(2))
                .andExpect(jsonPath("$.error.trace_id").value(TRACE_ID));
    }

    @Test
    void inventoryExceptionSupportsHttpStatusOverrideForAdjustmentInvalid() throws Exception {
        mockMvc.perform(get("/test/errors/adjustment-conflict")
                        .header("traceparent", TRACEPARENT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVENTORY_ADJUSTMENT_INVALID"))
                .andExpect(jsonPath("$.error.message").value("Resulting stock exceeds max limit"))
                .andExpect(jsonPath("$.error.details").doesNotExist())
                .andExpect(jsonPath("$.error.trace_id").value(TRACE_ID));
    }

    @Test
    void beanValidationErrorMapsToBadRequestInvalidInput() throws Exception {
        mockMvc.perform(post("/test/errors/validate")
                        .header("traceparent", TRACEPARENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVENTORY_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.trace_id").value(TRACE_ID));
    }

    @Test
    void malformedJsonBodyMapsToBadRequestInvalidInput() throws Exception {
        mockMvc.perform(post("/test/errors/validate")
                        .header("traceparent", TRACEPARENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVENTORY_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.trace_id").value(TRACE_ID));
    }

    @Test
    void typeMismatchMapsToBadRequestInvalidInput() throws Exception {
        mockMvc.perform(get("/test/errors/type-mismatch")
                        .param("sku_id", "not-a-uuid")
                        .header("traceparent", TRACEPARENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVENTORY_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.trace_id").value(TRACE_ID));
    }

    @Test
    void unexpectedExceptionMapsToInternalErrorWithoutLeakingInternalDetails() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected")
                        .header("traceparent", TRACEPARENT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INVENTORY_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc://secret"))))
                .andExpect(jsonPath("$.error.trace_id").value(TRACE_ID));
    }

    @Test
    void noResourceFoundMapsToNotFoundWithTraceId() throws Exception {
        mockMvc.perform(get("/test/errors/does-not-exist")
                        .header("traceparent", TRACEPARENT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.trace_id").value(TRACE_ID));
    }

    @Test
    void methodNotSupportedMapsToMethodNotAllowedWithTraceId() throws Exception {
        mockMvc.perform(post("/test/errors/insufficient")
                        .header("traceparent", TRACEPARENT))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.trace_id").value(TRACE_ID));
    }

    @Test
    void mediaTypeNotSupportedMapsToUnsupportedMediaTypeWithTraceId() throws Exception {
        mockMvc.perform(post("/test/errors/validate")
                        .header("traceparent", TRACEPARENT)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text body"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.error.trace_id").value(TRACE_ID));
    }

    @RestController
    static class StubErrorController {

        @GetMapping("/test/errors/insufficient")
        String insufficient() {
            throw new InventoryException(
                    ErrorCode.INVENTORY_STOCK_INSUFFICIENT,
                    "Not enough available stock",
                    Map.of("available", 2)
            );
        }

        @GetMapping("/test/errors/adjustment-conflict")
        String adjustmentConflict() {
            throw new InventoryException(
                    ErrorCode.INVENTORY_ADJUSTMENT_INVALID,
                    HttpStatus.CONFLICT,
                    "Resulting stock exceeds max limit"
            );
        }

        @PostMapping("/test/errors/validate")
        String validate(@Valid @RequestBody ValidationPayload payload) {
            return payload.reason();
        }

        @GetMapping("/test/errors/type-mismatch")
        String typeMismatch(@RequestParam("sku_id") UUID skuId) {
            return skuId.toString();
        }

        @GetMapping("/test/errors/unexpected")
        String unexpected() {
            throw new IllegalStateException("DB failure jdbc://secret-password");
        }
    }

    record ValidationPayload(
            @NotBlank String reason,
            @Min(1) int quantity
    ) {
    }
}
