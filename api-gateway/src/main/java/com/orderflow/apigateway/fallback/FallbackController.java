package com.orderflow.apigateway.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Reached when a route's CircuitBreaker filter is open or a call to the backend times out
 * (resilience4j.timelimiter, wired per-route in application.yml). Returns a stable 503 instead of
 * letting a slow/failing downstream service hang the gateway or propagate a confusing error.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/order-service")
    @PostMapping("/order-service")
    public ResponseEntity<Map<String, Object>> orderServiceFallback() {
        return unavailable("order-service is temporarily unavailable, please retry shortly");
    }

    @GetMapping("/inventory-service")
    @PostMapping("/inventory-service")
    public ResponseEntity<Map<String, Object>> inventoryServiceFallback() {
        return unavailable("inventory-service is temporarily unavailable, please retry shortly");
    }

    private ResponseEntity<Map<String, Object>> unavailable(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", 503, "error", "Service Unavailable", "message", message));
    }
}
