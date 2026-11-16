package com.taca.inventory.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaAdmin;

class HealthProbeServiceTest {

    @Test
    void readinessIsUpWhenDatabaseAndKafkaBothSucceed() throws Exception {
        DataSource dataSource = mockDatabase(true, null);
        KafkaAdmin kafkaAdmin = mockKafkaAdmin();
        AdminClient adminClient = mockKafka("cluster-1", null);
        HealthProbeService service = new HealthProbeService(dataSource, kafkaAdmin, props -> adminClient);

        HealthProbeService.ReadinessStatus status = service.checkReadiness();

        assertThat(status.isReady()).isTrue();
        assertThat(status.status()).isEqualTo("UP");
        assertThat(status.components()).containsEntry("database", "UP").containsEntry("kafka", "UP");
    }

    @Test
    void readinessIsDownWhenDatabaseConnectionIsInvalid() throws Exception {
        DataSource dataSource = mockDatabase(false, null);
        KafkaAdmin kafkaAdmin = mockKafkaAdmin();
        AdminClient adminClient = mockKafka("cluster-1", null);
        HealthProbeService service = new HealthProbeService(dataSource, kafkaAdmin, props -> adminClient);

        HealthProbeService.ReadinessStatus status = service.checkReadiness();

        assertThat(status.isReady()).isFalse();
        assertThat(status.status()).isEqualTo("DOWN");
        assertThat(status.components()).containsEntry("database", "DOWN").containsEntry("kafka", "UP");
    }

    @Test
    void readinessIsDownWhenDatabaseThrowsSqlException() throws Exception {
        DataSource dataSource = mockDatabase(false, new SQLException("Connection refused"));
        KafkaAdmin kafkaAdmin = mockKafkaAdmin();
        AdminClient adminClient = mockKafka("cluster-1", null);
        HealthProbeService service = new HealthProbeService(dataSource, kafkaAdmin, props -> adminClient);

        HealthProbeService.ReadinessStatus status = service.checkReadiness();

        assertThat(status.isReady()).isFalse();
        assertThat(status.components()).containsEntry("database", "DOWN").containsEntry("kafka", "UP");
    }

    @Test
    void readinessIsDownWhenKafkaDescribeClusterTimesOut() throws Exception {
        DataSource dataSource = mockDatabase(true, null);
        KafkaAdmin kafkaAdmin = mockKafkaAdmin();
        AdminClient adminClient = mockKafka(null, new TimeoutException("Timed out"));
        HealthProbeService service = new HealthProbeService(dataSource, kafkaAdmin, props -> adminClient);

        HealthProbeService.ReadinessStatus status = service.checkReadiness();

        assertThat(status.isReady()).isFalse();
        assertThat(status.status()).isEqualTo("DOWN");
        assertThat(status.components()).containsEntry("database", "UP").containsEntry("kafka", "DOWN");
    }

    @Test
    void readinessIsDownAndRestoresInterruptFlagWhenKafkaProbeInterrupted() throws Exception {
        DataSource dataSource = mockDatabase(true, null);
        KafkaAdmin kafkaAdmin = mockKafkaAdmin();
        AdminClient adminClient = mockKafka(null, new InterruptedException("Kafka probe interrupted"));
        AtomicBoolean workerInterruptFlagRestored = new AtomicBoolean(false);
        doAnswer(invocation -> {
            workerInterruptFlagRestored.set(Thread.currentThread().isInterrupted());
            return null;
        }).when(adminClient).close(any(Duration.class));

        HealthProbeService service = new HealthProbeService(dataSource, kafkaAdmin, props -> adminClient);

        try {
            HealthProbeService.ReadinessStatus status = service.checkReadiness();

            assertThat(status.isReady()).isFalse();
            assertThat(status.status()).isEqualTo("DOWN");
            assertThat(status.components()).containsEntry("database", "UP").containsEntry("kafka", "DOWN");
            assertThat(workerInterruptFlagRestored.get()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void readinessIsDownAndRestoresCallerInterruptFlagWhenAwaitingKafkaProbe() throws Exception {
        DataSource dataSource = mockDatabase(true, null);
        KafkaAdmin kafkaAdmin = mockKafkaAdmin();
        CountDownLatch kafkaProbeStarted = new CountDownLatch(1);
        CountDownLatch releaseKafkaProbe = new CountDownLatch(1);

        @SuppressWarnings("unchecked")
        KafkaFuture<String> blockingFuture = mock(KafkaFuture.class);
        when(blockingFuture.get(2L, TimeUnit.SECONDS)).thenAnswer(invocation -> {
            kafkaProbeStarted.countDown();
            releaseKafkaProbe.await(5, TimeUnit.SECONDS);
            return "cluster-1";
        });
        AdminClient adminClient = mock(AdminClient.class);
        DescribeClusterResult clusterResult = mock(DescribeClusterResult.class);
        when(adminClient.describeCluster(any(DescribeClusterOptions.class))).thenReturn(clusterResult);
        when(clusterResult.clusterId()).thenReturn(blockingFuture);

        Thread callerThread = Thread.currentThread();
        Thread interrupter = Thread.ofVirtual().start(() -> {
            try {
                if (kafkaProbeStarted.await(2, TimeUnit.SECONDS)) {
                    Thread.sleep(50);
                    callerThread.interrupt();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        HealthProbeService service = new HealthProbeService(dataSource, kafkaAdmin, props -> adminClient);

        try {
            HealthProbeService.ReadinessStatus status = service.checkReadiness();

            assertThat(status.isReady()).isFalse();
            assertThat(status.status()).isEqualTo("DOWN");
            assertThat(status.components()).containsEntry("database", "UP").containsEntry("kafka", "DOWN");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            releaseKafkaProbe.countDown();
            interrupter.join(1000);
        }
    }

    private DataSource mockDatabase(boolean valid, SQLException sqlException) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        if (sqlException != null) {
            when(dataSource.getConnection()).thenThrow(sqlException);
        } else {
            Connection connection = mock(Connection.class);
            when(connection.isValid(2)).thenReturn(valid);
            when(dataSource.getConnection()).thenReturn(connection);
        }
        return dataSource;
    }

    private KafkaAdmin mockKafkaAdmin() {
        KafkaAdmin kafkaAdmin = mock(KafkaAdmin.class);
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of("bootstrap.servers", "localhost:9092"));
        return kafkaAdmin;
    }

    @SuppressWarnings("unchecked")
    private AdminClient mockKafka(String clusterId, Exception failure) throws Exception {
        AdminClient adminClient = mock(AdminClient.class);
        DescribeClusterResult clusterResult = mock(DescribeClusterResult.class);
        KafkaFuture<String> clusterIdFuture = mock(KafkaFuture.class);
        when(adminClient.describeCluster(any(DescribeClusterOptions.class))).thenReturn(clusterResult);
        when(clusterResult.clusterId()).thenReturn(clusterIdFuture);
        if (failure != null) {
            when(clusterIdFuture.get(2L, TimeUnit.SECONDS)).thenThrow(failure);
        } else {
            when(clusterIdFuture.get(2L, TimeUnit.SECONDS)).thenReturn(clusterId);
        }
        return adminClient;
    }
}
