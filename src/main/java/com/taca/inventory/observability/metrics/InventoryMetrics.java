package com.taca.inventory.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Records bounded Micrometer timers for inventory operations and external dependency calls.
 */
@Component
public class InventoryMetrics {

    private static final String UNKNOWN = "unknown";

    private final MeterRegistry meterRegistry;

    public InventoryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records duration for a business or HTTP operation using bounded tags only.
     *
     * @param operation operation name (e.g., {@code reserve}, {@code commit})
     * @param result outcome ({@code success}, {@code error}, {@code conflict})
     * @param reason bounded outcome reason
     * @param duration measured elapsed duration
     */
    public void recordOperationDuration(String operation, String result, String reason, Duration duration) {
        if (duration == null) {
            return;
        }
        Timer.builder(BoundedMetricTags.OPERATION_DURATION)
                .tag(BoundedMetricTags.TAG_OPERATION, safeTagValue(operation))
                .tag(BoundedMetricTags.TAG_RESULT, safeTagValue(result))
                .tag(BoundedMetricTags.TAG_REASON, safeTagValue(reason))
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * Records duration for an infrastructure dependency call (e.g., {@code mysql}, {@code kafka}).
     *
     * @param serviceDependency dependency name
     * @param result outcome ({@code success}, {@code error})
     * @param reason bounded failure/outcome reason
     * @param duration measured elapsed duration
     */
    public void recordDependencyDuration(
            String serviceDependency,
            String result,
            String reason,
            Duration duration
    ) {
        if (duration == null) {
            return;
        }
        Timer.builder(BoundedMetricTags.DEPENDENCY_DURATION)
                .tag(BoundedMetricTags.TAG_SERVICE_DEPENDENCY, safeTagValue(serviceDependency))
                .tag(BoundedMetricTags.TAG_RESULT, safeTagValue(result))
                .tag(BoundedMetricTags.TAG_REASON, safeTagValue(reason))
                .register(meterRegistry)
                .record(duration);
    }

    private static String safeTagValue(String value) {
        return (value == null || value.isBlank()) ? UNKNOWN : value.trim();
    }
}
