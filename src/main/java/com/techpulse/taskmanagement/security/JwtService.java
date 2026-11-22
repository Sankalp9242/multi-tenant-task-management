package com.techpulse.taskmanagement.security;

import com.techpulse.taskmanagement.entity.User;
import com.techpulse.taskmanagement.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration:PT1H}") Duration expiration) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 UTF-8 bytes");
        }
        if (expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("JWT expiration must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
    }

    public String createToken(User user) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("user_id", user.getId())
                .claim("tenant_id", user.getTenant().getId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token).getPayload();
        Object tenantValue = claims.get("tenant_id");
        Object userValue = claims.get("user_id");
        if (!(tenantValue instanceof Number tenantNumber)
                || !(userValue instanceof Number userNumber)
                || claims.getSubject() == null
                || claims.get("role", String.class) == null) {
            throw new IllegalArgumentException("Required JWT claims are missing");
        }
        Role role = Role.valueOf(claims.get("role", String.class));
        return new AuthenticatedUser(userNumber.longValue(), claims.getSubject(), role, tenantNumber.longValue());
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }
}
