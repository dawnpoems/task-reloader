package com.yegkim.task_reloader_api.auth.jwt;

import com.yegkim.task_reloader_api.auth.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String ACCESS_TYPE = "ACCESS";

    private final SecretKey secretKey;
    private final long accessTokenTtlSeconds;

    public JwtTokenProvider(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds
    ) {
        this.secretKey = resolveSecretKey(secret);
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public String generateAccessToken(Long userId, UserRole role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, ACCESS_TYPE)
                .signWith(secretKey)
                .compact();
    }

    public AccessTokenPayload parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get(CLAIM_TYPE, String.class);
        if (!ACCESS_TYPE.equals(tokenType)) {
            throw new JwtException("Invalid token type");
        }

        Long userId = Long.valueOf(claims.getSubject());
        UserRole role = UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
        return new AccessTokenPayload(userId, role);
    }

    public boolean isValidAccessToken(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey resolveSecretKey(String secret) {
        String sanitized = secret.trim();
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("auth.jwt.secret must not be empty");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(sanitized);
        } catch (IllegalArgumentException ignored) {
            keyBytes = sanitized.getBytes(StandardCharsets.UTF_8);
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }
}
