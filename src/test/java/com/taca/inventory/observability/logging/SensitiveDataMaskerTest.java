package com.taca.inventory.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    @Test
    void masksAuthorizationBearerAndJwtTokensInText() {
        String raw = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMifQ.sig123";
        String masked = masker.mask(raw);

        assertThat(masked).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(masked).doesNotContain("sig123");
        assertThat(masked).contains("[REDACTED]");
    }

    @Test
    void masksInternalServiceTokenAndSensitiveHeaders() {
        assertThat(masker.maskHeader("Authorization", "Bearer super-secret-jwt")).isEqualTo("[REDACTED]");
        assertThat(masker.maskHeader("X-Internal-Service-Token", "my-internal-secret")).isEqualTo("[REDACTED]");
        assertThat(masker.maskHeader("Cookie", "session=abc")).isEqualTo("[REDACTED]");
        assertThat(masker.maskHeader("X-Request-ID", "req-123")).isEqualTo("req-123");
    }

    @Test
    void masksPaymentCardEmailPhoneAndAddressFieldsInMapsAndJsonText() {
        Map<String, Object> input = Map.of(
                "order_id", "ord-1",
                "token", "secret-token-value",
                "authorization", "Bearer abc.def.ghi",
                "card_number", "4111111111111111",
                "cvv", "999",
                "email", "buyer@example.com",
                "phone", "+84901234567",
                "shipping_address", "123 Le Loi, Q1, HCMC",
                "nested", Map.of("password", "hunter2", "sku_id", "sku-1")
        );

        Map<String, Object> masked = masker.maskMap(input);

        assertThat(masked.get("order_id")).isEqualTo("ord-1");
        assertThat(masked.get("token")).isEqualTo("[REDACTED]");
        assertThat(masked.get("authorization")).isEqualTo("[REDACTED]");
        assertThat(masked.get("card_number")).isEqualTo("[REDACTED]");
        assertThat(masked.get("cvv")).isEqualTo("[REDACTED]");
        assertThat(masked.get("email")).isEqualTo("[REDACTED]");
        assertThat(masked.get("phone")).isEqualTo("[REDACTED]");
        assertThat(masked.get("shipping_address")).isEqualTo("[REDACTED]");

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) masked.get("nested");
        assertThat(nested.get("password")).isEqualTo("[REDACTED]");
        assertThat(nested.get("sku_id")).isEqualTo("sku-1");

        String rawJson = "{\"token\":\"abc123\",\"email\":\"user@domain.com\",\"sku_id\":\"sku-1\"}";
        String maskedJson = masker.mask(rawJson);
        assertThat(maskedJson).doesNotContain("abc123");
        assertThat(maskedJson).doesNotContain("user@domain.com");
        assertThat(maskedJson).contains("sku-1");
    }

    @Test
    void masksPrefixedAndSuffixedPiiKeysInMapsAndSensitiveHeaderFieldsInJson() {
        Map<String, Object> input = Map.of(
                "recipient_phone", "+84909998877",
                "delivery_address", "456 Nguyen Hue, Q1",
                "x-api-key", "api-key-secret-value",
                "sku_id", "sku-42"
        );

        Map<String, Object> masked = masker.maskMap(input);

        assertThat(masked.get("recipient_phone")).isEqualTo("[REDACTED]");
        assertThat(masked.get("delivery_address")).isEqualTo("[REDACTED]");
        assertThat(masked.get("x-api-key")).isEqualTo("[REDACTED]");
        assertThat(masked.get("sku_id")).isEqualTo("sku-42");

        String rawJson = "{\"x-internal-service-token\":\"internal-secret\","
                + "\"cookie\":\"sid=secret-cookie\","
                + "\"x-api-key\":\"key-secret\","
                + "\"sku_id\":\"sku-42\"}";
        String maskedJson = masker.mask(rawJson);

        assertThat(maskedJson).doesNotContain("internal-secret");
        assertThat(maskedJson).doesNotContain("secret-cookie");
        assertThat(maskedJson).doesNotContain("key-secret");
        assertThat(maskedJson).contains("\"x-internal-service-token\":\"[REDACTED]\"");
        assertThat(maskedJson).contains("\"cookie\":\"[REDACTED]\"");
        assertThat(maskedJson).contains("\"x-api-key\":\"[REDACTED]\"");
        assertThat(maskedJson).contains("\"sku_id\":\"sku-42\"");
    }
}
