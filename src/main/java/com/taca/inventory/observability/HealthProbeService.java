package com.taca.inventory.observability;

import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

/**
 * Checks MySQL and Kafka connectivity with bounded 2-second timeouts for readiness probes.
 */
@Service
public class HealthProbeService {

    private static final int PROBE_TIMEOUT_SECONDS = 2;
    private static final int PROBE_TIMEOUT_MILLIS = 2000;
    private static final long PROBE_AWAIT_SLACK_MILLIS = 500L;
    private static final Duration ADMIN_CLOSE_TIMEOUT = Duration.ofSeconds(1);
    private static final Executor VIRTUAL_THREAD_EXECUTOR = task -> Thread.ofVirtual().start(task);
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    private final DataSource dataSource;
    private final KafkaAdmin kafkaAdmin;
    private final Function<Map<String, Object>, AdminClient> adminClientFactory;

    @Autowired
    public HealthProbeService(DataSource dataSource, KafkaAdmin kafkaAdmin) {
        this(dataSource, kafkaAdmin, AdminClient::create);
    }

    public HealthProbeService(
            DataSource dataSource,
            KafkaAdmin kafkaAdmin,
            Function<Map<String, Object>, AdminClient> adminClientFactory
    ) {
        this.dataSource = dataSource;
        this.kafkaAdmin = kafkaAdmin;
        this.adminClientFactory = adminClientFactory;
    }

    /**
     * Evaluates database and Kafka readiness concurrently within bounded timeouts.
     *
     * @return aggregated {@link ReadinessStatus}
     */
    public ReadinessStatus checkReadiness() {
        CompletableFuture<Boolean> dbFuture = CompletableFuture.supplyAsync(
                this::isDatabaseReady, VIRTUAL_THREAD_EXECUTOR);
        CompletableFuture<Boolean> kafkaFuture = CompletableFuture.supplyAsync(
                this::isKafkaReady, VIRTUAL_THREAD_EXECUTOR);

        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(PROBE_TIMEOUT_MILLIS + PROBE_AWAIT_SLACK_MILLIS);
        boolean dbUp = awaitProbe(dbFuture, deadlineNanos);
        boolean kafkaUp = awaitProbe(kafkaFuture, deadlineNanos);

        Map<String, String> components = new LinkedHashMap<>();
        components.put("database", dbUp ? UP : DOWN);
        components.put("kafka", kafkaUp ? UP : DOWN);

        return new ReadinessStatus(dbUp && kafkaUp, Map.copyOf(components));
    }

    private boolean awaitProbe(CompletableFuture<Boolean> future, long deadlineNanos) {
        long remainingNanos = Math.max(1L, deadlineNanos - System.nanoTime());
        try {
            return Boolean.TRUE.equals(future.get(remainingNanos, TimeUnit.NANOSECONDS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return false;
        } catch (Exception ex) {
            future.cancel(true);
            return false;
        }
    }

    private boolean isDatabaseReady() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(PROBE_TIMEOUT_SECONDS);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isKafkaReady() {
        AdminClient adminClient = null;
        try {
            adminClient = adminClientFactory.apply(kafkaAdmin.getConfigurationProperties());
            String clusterId = adminClient
                    .describeCluster(new DescribeClusterOptions().timeoutMs(PROBE_TIMEOUT_MILLIS))
                    .clusterId()
                    .get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return clusterId != null && !clusterId.isBlank();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            return false;
        } finally {
            if (adminClient != null) {
                try {
                    adminClient.close(ADMIN_CLOSE_TIMEOUT);
                } catch (Exception ignored) {
                    // Ignore AdminClient close error during readiness probe cleanup
                }
            }
        }
    }

    /**
     * Aggregated readiness probe result.
     *
     * @param isReady {@code true} when all required dependencies are healthy
     * @param components per-dependency status map
     */
    public record ReadinessStatus(boolean isReady, Map<String, String> components) {

        /**
         * Returns {@code "UP"} if ready, otherwise {@code "DOWN"}.
         *
         * @return readiness status string
         */
        public String status() {
            return isReady ? UP : DOWN;
        }
    }
}
