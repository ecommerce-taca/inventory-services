package com.taca.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taca.inventory.observability.HealthProbeService;
import com.taca.inventory.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "inventory.security.internal-token=test-internal-token",
                "inventory.low-stock-threshold=5"
        }
)
@AutoConfigureMockMvc
@Testcontainers
class InventoryApplicationTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("inventorydb")
            .withUsername("inventory")
            .withPassword("inventory");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String baseUrl = MYSQL.getJdbcUrl();
            String separator = baseUrl.contains("?") ? "&" : "?";
            return baseUrl + separator + "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        });
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HealthProbeService healthProbeService;

    @Autowired
    private SecurityProperties securityProperties;

    @Test
    void contextLoadsAndHealthEndpointsReportUpAgainstMySqlAndKafka() throws Exception {
        assertThat(securityProperties.getInternalToken()).isEqualTo("test-internal-token");
        assertThat(healthProbeService.checkReadiness().isReady()).isTrue();

        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.database").value("UP"))
                .andExpect(jsonPath("$.components.kafka").value("UP"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
