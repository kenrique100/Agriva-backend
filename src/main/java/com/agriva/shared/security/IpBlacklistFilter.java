package com.agriva.shared.security;

import com.agriva.shared.security.blacklist.IpBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Earliest guard in the chain. If the client IP has been blacklisted after
 * too many failed logins, reject with 429 + Retry-After without ever
 * touching Bcrypt, the JWT parser, or the database.
 */
@Slf4j
@RequiredArgsConstructor
public class IpBlacklistFilter extends OncePerRequestFilter {

    private final IpBlacklistService blacklistService;
    private final TrustedProxyResolver proxyResolver;

    @Value("${agriva.security.ip-blacklist.enabled:true}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws ServletException, IOException {

        if (!enabled) { chain.doFilter(req, res); return; }

        String ip = proxyResolver.resolve(req);
        if (blacklistService.isBlocked(ip)) {
            long remaining = Math.max(1, blacklistService.blockRemainingSeconds(ip));
            log.warn("Rejected request from blocked IP {} ({}s remaining)", ip, remaining);
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(remaining));
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.getWriter().write(
                    "{\"status\":429," +
                            "\"message\":\"IP temporarily blocked due to repeated failed logins\"," +
                            "\"retryAfter\":" + remaining + "}");
            return;
        }
        chain.doFilter(req, res);
    }
}