package com.taca.inventory.observability.metrics;

import java.util.Map;
import java.util.Set;

/**
 * Defines bounded metric names and enforces low-cardinality tag keys.
 */
public final class BoundedMetricTags {

    public static final String OPERATION_DURATION = "inventory.operation.duration";
    public static final String DEPENDENCY_DURATION = "inventory.dependency.duration";

    public static final String TAG_OPERATION = "operation";
    public static final String TAG_RESULT = "result";
    public static final String TAG_REASON = "reason";
    public static final String TAG_SERVICE_DEPENDENCY = "service_dependency";

    private static final Set<String> ALLOWED_TAG_KEYS = Set.of(
            TAG_OPERATION,
            TAG_RESULT,
            TAG_REASON,
            TAG_SERVICE_DEPENDENCY
    );

    private BoundedMetricTags() {
    }

    /**
     * Validates that all supplied metric tag keys belong to the bounded allowlist.
     *
     * @param tags candidate metric tags
     */
    public static void validateTagKeys(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String key : tags.keySet()) {
            if (!ALLOWED_TAG_KEYS.contains(key)) {
                throw new IllegalArgumentException("Disallowed high-cardinality metric tag key: " + key);
            }
        }
    }
}
