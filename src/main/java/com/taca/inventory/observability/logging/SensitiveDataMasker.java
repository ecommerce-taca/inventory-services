package com.taca.inventory.observability.logging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Redacts credentials, tokens, payment fields, and PII before logging.
 */
@Component
public class SensitiveDataMasker {

    public static final String REDACTED = "[REDACTED]";

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "proxy-authorization",
            "x-internal-service-token",
            "cookie",
            "set-cookie",
            "x-api-key"
    );

    private static final List<String> SENSITIVE_KEY_LIST = List.of(
            "authorization",
            "proxy-authorization",
            "token",
            "access_token",
            "refresh_token",
            "internal_token",
            "x-internal-service-token",
            "password",
            "secret",
            "api_key",
            "apikey",
            "x-api-key",
            "cookie",
            "set-cookie",
            "card_number",
            "cardnumber",
            "cvv",
            "cvc",
            "email",
            "phone",
            "mobile",
            "address",
            "shipping_address",
            "billing_address"
    );

    private static final Set<String> SENSITIVE_KEYS = Set.copyOf(SENSITIVE_KEY_LIST);

    private static final List<String> SENSITIVE_KEY_SUBSTRINGS = List.of(
            "password",
            "secret",
            "token",
            "authorization",
            "card_number",
            "cardnumber",
            "cvv",
            "cvc",
            "email",
            "phone",
            "mobile",
            "address",
            "api_key",
            "apikey",
            "x-api-key",
            "cookie"
    );

    private static final Pattern BEARER_PATTERN =
            Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9\\-._~+/]+=*");
    private static final Pattern JWT_PATTERN =
            Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("(?i)jdbc:[a-z0-9:]*//\\S+");
    private static final Pattern KEY_VALUE_SECRET_PATTERN =
            Pattern.compile("(?i)(password|secret|token|api_key|apikey)\\s*([=:])\\s*\\S+");
    private static final Pattern JSON_SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)\"(" + SENSITIVE_KEY_LIST.stream()
                    .map(Pattern::quote)
                    .collect(Collectors.joining("|"))
                    + ")\"\\s*:\\s*\"[^\"]*\""
    );
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /**
     * Masks bearer tokens, JWTs, JDBC URLs, emails, and sensitive JSON/key-value fields in arbitrary log strings.
     *
     * @param raw input text
     * @return sanitized text, or {@code null} if input was {@code null}
     */
    public String mask(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String sanitized = BEARER_PATTERN.matcher(raw).replaceAll("$1" + REDACTED);
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll(REDACTED);
        sanitized = JDBC_URL_PATTERN.matcher(sanitized).replaceAll(REDACTED);
        sanitized = maskJsonFields(sanitized);
        sanitized = KEY_VALUE_SECRET_PATTERN.matcher(sanitized).replaceAll("$1$2" + REDACTED);
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(REDACTED);
        return sanitized;
    }

    /**
     * Masks sensitive HTTP header values while leaving safe headers intact.
     *
     * @param headerName HTTP header name
     * @param headerValue HTTP header value
     * @return {@code [REDACTED]} for sensitive headers, otherwise masked value
     */
    public String maskHeader(String headerName, String headerValue) {
        if (headerValue == null) {
            return null;
        }
        if (headerName != null && SENSITIVE_HEADERS.contains(headerName.trim().toLowerCase(Locale.ROOT))) {
            return REDACTED;
        }
        return mask(headerValue);
    }

    /**
     * Recursively masks sensitive keys and string values inside structured maps.
     *
     * @param input raw map
     * @return new map with sensitive values redacted
     */
    public Map<String, Object> maskMap(Map<String, ?> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        return maskMapEntries(input);
    }

    private Map<String, Object> maskMapEntries(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
            if (isSensitiveKey(key)) {
                result.put(key, REDACTED);
            } else {
                result.put(key, maskValue(entry.getValue()));
            }
        }
        return result;
    }

    private Object maskValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> mapValue) {
            return maskMapEntries(mapValue);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> maskedList = new ArrayList<>(collection.size());
            for (Object item : collection) {
                maskedList.add(maskValue(item));
            }
            return maskedList;
        }
        if (value instanceof String strValue) {
            return mask(strValue);
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (SENSITIVE_KEYS.contains(normalized)) {
            return true;
        }
        for (String substring : SENSITIVE_KEY_SUBSTRINGS) {
            if (normalized.contains(substring)) {
                return true;
            }
        }
        return false;
    }

    private static String maskJsonFields(String input) {
        Matcher matcher = JSON_SENSITIVE_FIELD_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            matcher.appendReplacement(sb, Matcher.quoteReplacement("\"" + fieldName + "\":\"" + REDACTED + "\""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
