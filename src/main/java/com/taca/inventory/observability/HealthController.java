package com.taca.inventory.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes un-enveloped {@code /health/live} and {@code /health/ready} Kubernetes/ops probes.
 */
@RestController
public class HealthController {

    private final HealthProbeService healthProbeService;

    public HealthController(HealthProbeService healthProbeService) {
        this.healthProbeService = healthProbeService;
    }

    /**
     * Process liveness endpoint; never queries external dependencies.
     *
     * @return {@code 200 {"status":"UP"}}
     */
    @GetMapping("/health/live")
    public ResponseEntity<Map<String, Object>> live() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    /**
     * Readiness endpoint checking MySQL and Kafka connectivity.
     *
     * @return {@code 200} when all components are UP, {@code 503} otherwise
     */
    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        HealthProbeService.ReadinessStatus readiness = healthProbeService.checkReadiness();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", readiness.status());
        body.put("components", readiness.components());

        HttpStatus status = readiness.isReady() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(body);
    }
}
