package com.agriva.identity.utils;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@Slf4j
public class HibpService {

    private final RestClient restClient;

    public HibpService(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://api.pwnedpasswords.com").build();
    }

    /**
     * HIBP k-anonymity — only the first 5 chars of the SHA-1 leave the JVM.
     * Protected by the "hibp" circuit breaker declared in
     * application.properties. Falls back to {@code false} (allow the password)
     * when the breaker is open or the HTTP call fails.
     */
    @CircuitBreaker(name = "hibp", fallbackMethod = "isPwnedFallback")
    public boolean isPwned(String password) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1")
                    .digest(password.getBytes(StandardCharsets.UTF_8));
            String sha1   = HexFormat.of().formatHex(hash).toUpperCase();
            String prefix = sha1.substring(0, 5);
            String suffix = sha1.substring(5);

            String body = restClient.get()
                    .uri("/range/{prefix}", prefix)
                    .retrieve()
                    .body(String.class);

            if (body == null) return false;
            return body.lines()
                    .map(l -> l.split(":", 2)[0])
                    .anyMatch(suffix::equals);
        } catch (Exception e) {
            // The circuit breaker will see this and may open.
            throw new RuntimeException("HIBP lookup failed", e);
        }
    }

    @SuppressWarnings("unused")
    private boolean isPwnedFallback(String password, Throwable t) {
        log.warn("HIBP circuit open / call failed, allowing password (fail-open): {}",
                t.getMessage());
        return false;
    }
}