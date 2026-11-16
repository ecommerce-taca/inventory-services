package com.taca.inventory.observability;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taca.inventory.common.error.GlobalExceptionHandler;
import com.taca.inventory.observability.logging.HttpLoggingFilter;
import com.taca.inventory.observability.logging.JsonStructuredLogFormatter;
import com.taca.inventory.observability.logging.SensitiveDataMasker;
import com.taca.inventory.observability.trace.TraceContextFilter;
import com.taca.inventory.security.ActorContextFilter;
import com.taca.inventory.security.SecurityProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@Import({
        HealthController.class,
        GlobalExceptionHandler.class,
        TraceContextFilter.class,
        ActorContextFilter.class,
        HttpLoggingFilter.class,
        JsonStructuredLogFormatter.class,
        SensitiveDataMasker.class
})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "inventory.security.internal-token=test-internal-token")
class HealthProbeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthProbeService healthProbeService;

    @Test
    void livenessReturns200UpWithoutCallingDatabaseOrKafkaAndWithoutDataEnvelope() throws Exception {
        mockMvc.perform(get("/health/live").header("X-Request-ID", "req-live-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "req-live-01"))
                .andExpect(header().exists("traceparent"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());

        verifyNoInteractions(healthProbeService);
    }

    @Test
    void readinessReturns200WhenDatabaseAndKafkaAreUp() throws Exception {
        when(healthProbeService.checkReadiness())
                .thenReturn(new HealthProbeService.ReadinessStatus(true, Map.of("database", "UP", "kafka", "UP")));

        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.database").value("UP"))
                .andExpect(jsonPath("$.components.kafka").value("UP"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void readinessReturns503WhenDatabaseIsDown() throws Exception {
        when(healthProbeService.checkReadiness())
                .thenReturn(new HealthProbeService.ReadinessStatus(false, Map.of("database", "DOWN", "kafka", "UP")));

        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.database").value("DOWN"))
                .andExpect(jsonPath("$.components.kafka").value("UP"));
    }

    @Test
    void readinessReturns503WhenKafkaIsDown() throws Exception {
        when(healthProbeService.checkReadiness())
                .thenReturn(new HealthProbeService.ReadinessStatus(false, Map.of("database", "UP", "kafka", "DOWN")));

        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.database").value("UP"))
                .andExpect(jsonPath("$.components.kafka").value("DOWN"));
    }
}
