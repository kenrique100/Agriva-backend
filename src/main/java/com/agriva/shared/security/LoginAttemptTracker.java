package com.agriva.shared.security;

import com.agriva.shared.security.blacklist.IpBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Bridges the login service to the IP blacklist without threading a
 * request-scoped object through every call. Reads the current request from
 * RequestContextHolder, resolves the real client IP safely, and records /
 * clears failed-login counters.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptTracker {

    private final TrustedProxyResolver proxyResolver;
    private final IpBlacklistService blacklistService;

    public void recordFailure() {
        String ip = currentClientIp();
        if (ip != null) blacklistService.recordFailure(ip);
    }

    public void clear() {
        String ip = currentClientIp();
        if (ip != null) blacklistService.clearFailures(ip);
    }

    private String currentClientIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) return null;
        HttpServletRequest req = sra.getRequest();
        return proxyResolver.resolve(req);
    }
}