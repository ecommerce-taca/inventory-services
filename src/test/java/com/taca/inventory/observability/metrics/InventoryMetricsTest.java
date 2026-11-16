package com.taca.inventory.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InventoryMetricsTest {

    @Test
    void recordsOperationAndDependencyDurationTimersWithBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InventoryMetrics metrics = new InventoryMetrics(registry);

        metrics.recordOperationDuration("reserve", "success", "ok", Duration.ofMillis(25));
        metrics.recordDependencyDuration("mysql", "error", "lock_timeout", Duration.ofMillis(120));

        Timer opTimer = registry.find(BoundedMetricTags.OPERATION_DURATION)
                .tag(BoundedMetricTags.TAG_OPERATION, "reserve")
                .tag(BoundedMetricTags.TAG_RESULT, "success")
                .tag(BoundedMetricTags.TAG_REASON, "ok")
                .timer();
        assertThat(opTimer).isNotNull();
        assertThat(opTimer.count()).isEqualTo(1L);
        assertThat(opTimer.getId().getTags())
                .extracting(tag -> tag.getKey())
                .doesNotContain("sku_id", "order_id", "user_id", "reservation_id", "request_id", "trace_id");

        Timer depTimer = registry.find(BoundedMetricTags.DEPENDENCY_DURATION)
                .tag(BoundedMetricTags.TAG_SERVICE_DEPENDENCY, "mysql")
                .tag(BoundedMetricTags.TAG_RESULT, "error")
                .tag(BoundedMetricTags.TAG_REASON, "lock_timeout")
                .timer();
        assertThat(depTimer).isNotNull();
        assertThat(depTimer.count()).isEqualTo(1L);
        assertThat(depTimer.getId().getTags())
                .extracting(tag -> tag.getKey())
                .doesNotContain("sku_id", "order_id", "user_id", "reservation_id", "request_id", "trace_id");
    }

    @Test
    void boundedMetricTagsRejectsHighCardinalityLabels() {
        BoundedMetricTags.validateTagKeys(Map.of(
                BoundedMetricTags.TAG_OPERATION, "adjust",
                BoundedMetricTags.TAG_RESULT, "success",
                BoundedMetricTags.TAG_REASON, "none"
        ));

        assertThatThrownBy(() -> BoundedMetricTags.validateTagKeys(Map.of("sku_id", "sku-123")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoundedMetricTags.validateTagKeys(Map.of("order_id", "ord-123")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoundedMetricTags.validateTagKeys(Map.of("user_id", "usr-123")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
