package com.agriva.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-JVM rate limiter using a bounded Caffeine cache (evicts idle buckets,
 * hard memory cap) — see audit A5.
 *
 * For horizontal scaling, swap the cache for bucket4j-redis. The identity
 * resolution and Rule shape stay the same.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${agriva.rate-limit.enabled:true}")                    private boolean enabled;
    @Value("${agriva.rate-limit.login-per-minute:5}")              private int loginPerMin;
    @Value("${agriva.rate-limit.register-per-hour-per-ip:5}")      private int registerMasterPerHour;
    @Value("${agriva.rate-limit.sub-user-per-hour-per-user:30}")   private int subUserPerHour;
    @Value("${agriva.rate-limit.forgot-password-per-hour-per-email:3}") private int forgotPerHour;
    @Value("${agriva.rate-limit.default-per-minute:120}")          private int defaultPerMin;
    @Value("${agriva.rate-limit.upload-per-minute:30}")            private int uploadPerMin;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(200_000)
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse resp,
                                    FilterChain chain)
            throws ServletException, IOException {

        if (!enabled) { chain.doFilter(req, resp); return; }

        Rule rule = resolveRule(req);
        String key = rule.name + ":" + identity(req, rule);
        Bucket bucket = buckets.get(key, k -> newBucket(rule));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            resp.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(req, resp);
            return;
        }

        long waitSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        resp.setStatus(429);
        resp.setHeader("Retry-After", String.valueOf(waitSeconds));
        resp.setHeader("X-RateLimit-Limit", String.valueOf(rule.capacity));
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.getWriter().write(
                "{\"status\":429,\"message\":\"Too many requests\",\"retryAfter\":" + waitSeconds + "}");
    }

    private Rule resolveRule(HttpServletRequest req) {
        String path   = req.getRequestURI();
        String method = req.getMethod();

        if (path.equals("/api/v1/users/login") && method.equals("POST")) {
            return new Rule("login", loginPerMin, Duration.ofMinutes(1));
        }
        if (path.equals("/api/v1/users/register/master") && method.equals("POST")) {
            return new Rule("register-master", registerMasterPerHour, Duration.ofHours(1));
        }
        if (path.equals("/api/v1/users/forgot-password") && method.equals("POST")) {
            return new Rule("forgot", forgotPerHour, Duration.ofHours(1));
        }
        if (path.equals("/api/v1/users/register/sub-user") && method.equals("POST")) {
            return new Rule("register-sub-user", subUserPerHour, Duration.ofHours(1));
        }
        if (path.startsWith("/api/v1/files/") && method.equals("GET")) {
            return new Rule("file-get", defaultPerMin * 4, Duration.ofMinutes(1));
        }
        if ((path.equals("/api/v1/expenses") && method.equals("POST"))
                || (path.equals("/api/v1/incomes") && method.equals("POST"))
                || (path.endsWith("/picture") && method.equals("POST"))) {
            return new Rule("upload", uploadPerMin, Duration.ofMinutes(1));
        }

        return new Rule("default", defaultPerMin, Duration.ofMinutes(1));
    }

    private String identity(HttpServletRequest req, Rule rule) {
        if (rule.name.equals("login")
                || rule.name.equals("register-master")
                || rule.name.equals("forgot")) {
            return clientIp(req);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return "u:" + u.userId();
        }
        return "ip:" + clientIp(req);
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private Bucket newBucket(Rule r) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(r.capacity)
                        .refillIntervally(r.capacity, r.window)
                        .build())
                .build();
    }

    private record Rule(String name, int capacity, Duration window) {}
}