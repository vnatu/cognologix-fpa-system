package com.cognologix.fpa.security;

import com.cognologix.fpa.general.GeneralConfigService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long fallbackExpirationMs;
    private final GeneralConfigService generalConfigService;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:7200000}") long fallbackExpirationMs,
            GeneralConfigService generalConfigService) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.fallbackExpirationMs = fallbackExpirationMs;
        this.generalConfigService = generalConfigService;
    }

    public String generateToken(String username, String role, boolean mustChangePassword) {
        return generateToken(username, role, mustChangePassword, resolveExpirationMs());
    }

    /**
     * Generates a token with an explicit lifetime — used by tests (e.g. expired JWT).
     */
    public String generateToken(
            String username, String role, boolean mustChangePassword, long expirationMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("mustChangePassword", mustChangePassword)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public long resolveExpirationMs() {
        try {
            int hours = generalConfigService.getJwtExpiryHours();
            return Duration.ofHours(hours).toMillis();
        } catch (Exception e) {
            return fallbackExpirationMs;
        }
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        Object role = parseClaims(token).get("role");
        return role != null ? role.toString() : null;
    }

    public boolean extractMustChangePassword(String token) {
        Boolean value = parseClaims(token).get("mustChangePassword", Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
