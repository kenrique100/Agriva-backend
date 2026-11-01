package com.agriva.security;

import com.agriva.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessTtlSeconds;
    private final String issuer;

    public JwtUtil(
            @Value("${agriva.security.jwt.secret}") String secret,
            @Value("${agriva.security.jwt.access-token-ttl-seconds:900}") long accessTtlSeconds,
            @Value("${agriva.security.jwt.issuer:agriva}") String issuer) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 64) {
            throw new IllegalStateException(
                    "agriva.security.jwt.secret must be at least 64 bytes for HS512");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtlSeconds = accessTtlSeconds;
        this.issuer = issuer;
    }

    public long getAccessTtlSeconds() { return accessTtlSeconds; }

    public String generateToken(User user, String jti) {
        String normalizedRole = RoleConstants.normalizeRole(user.getRole());
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(jti)
                .issuer(issuer)
                .subject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("farmId", user.getFarmId() == null ? null : user.getFarmId().toString())
                .claim("username", user.getUsername())
                .claim("role", normalizedRole)
                .claim("roles", List.of(normalizedRole))
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTtlSeconds * 1000))
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try { parseClaims(token); return true; } catch (Exception e) { return false; }
    }

    public String extractEmail(String token)     { return parseClaims(token).getSubject(); }
    public String extractJti(String token)       { return parseClaims(token).getId(); }
    public Date   extractIssuedAt(String token)  { return parseClaims(token).getIssuedAt(); }

    public UUID extractUserId(String token) {
        try {
            String v = parseClaims(token).get("userId", String.class);
            return v != null ? UUID.fromString(v) : null;
        } catch (Exception e) { return null; }
    }

    public UUID extractFarmId(String token) {
        try {
            String v = parseClaims(token).get("farmId", String.class);
            return v == null || v.isBlank() ? null : UUID.fromString(v);
        } catch (Exception e) { return null; }
    }

    public String extractUsername(String token) {
        try { return parseClaims(token).get("username", String.class); }
        catch (Exception e) { return null; }
    }

    public String extractRole(String token) {
        try {
            String v = parseClaims(token).get("role", String.class);
            return RoleConstants.normalizeRole(v);
        } catch (Exception e) {
            return RoleConstants.DEFAULT_ROLE;
        }
    }

    public Date extractExpiration(String token)  { return parseClaims(token).getExpiration(); }

    public long getSecondsUntilExpiry(String token) {
        Date exp = extractExpiration(token);
        return Math.max(0, (exp.getTime() - System.currentTimeMillis()) / 1000L);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}