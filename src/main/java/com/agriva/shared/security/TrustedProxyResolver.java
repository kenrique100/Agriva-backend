package com.agriva.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves the real client IP from an HTTP request.
 * <p>
 * SECURITY: X-Forwarded-For / X-Real-IP are trusted ONLY when the direct
 * peer (request.getRemoteAddr()) is in a configured trusted proxy range
 * (e.g. your load balancer). Otherwise, an attacker sets
 * "X-Forwarded-For: 1.2.3.4" on every request and bypasses both this
 * filter and the RateLimitFilter.
 * <p>
 * Set agriva.security.proxy.trusted-cidrs=10.0.0.0/8,172.16.0.0/12
 * or leave it blank if the app is not behind a reverse proxy.
 */
@Component
public class TrustedProxyResolver {

    @Value("${agriva.security.proxy.trusted-cidrs:}")
    private String trustedCidrsCsv;

    public String resolve(HttpServletRequest req) {
        String peer = req.getRemoteAddr();
        if (!isTrusted(peer)) {
            return peer;
        }

        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            for (String hop : xff.split(",")) {
                String ip = hop.trim();
                if (!ip.isEmpty() && !isTrusted(ip)) {
                    return ip;
                }
            }
        }

        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return peer;
    }

    boolean isTrusted(String ip) {
        if (trustedCidrsCsv == null || trustedCidrsCsv.isBlank()) return false;
        try {
            InetAddress addr = InetAddress.getByName(ip);
            for (String cidr : trustedCidrsCsv.split(",")) {
                if (cidrMatches(addr, cidr.trim())) return true;
            }
        } catch (UnknownHostException ignored) { }
        return false;
    }

    private boolean cidrMatches(InetAddress addr, String cidr) {
        if (cidr.isEmpty()) return false;
        try {
            int slash = cidr.indexOf('/');
            if (slash < 0) {
                return addr.equals(InetAddress.getByName(cidr));
            }
            InetAddress network = InetAddress.getByName(cidr.substring(0, slash));
            int prefix = Integer.parseInt(cidr.substring(slash + 1));

            byte[] a = addr.getAddress();
            byte[] n = network.getAddress();
            if (a.length != n.length) return false;

            int fullBytes = prefix / 8;
            int remainder = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (a[i] != n[i]) return false;
            }
            if (remainder == 0) return true;
            int mask = 0xFF << (8 - remainder);
            return (a[fullBytes] & mask) == (n[fullBytes] & mask);
        } catch (Exception e) {
            return false;
        }
    }
}