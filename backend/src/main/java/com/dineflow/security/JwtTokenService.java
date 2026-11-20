package com.dineflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, UUID tenantId, String roleCode, List<String> permissions, String type) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getAccessTokenExpirationSeconds());

        var builder = Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(userId.toString())
                .claim("role", roleCode)
                .claim("permissions", permissions)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey);

        if (tenantId != null) {
            builder.claim("tenantId", tenantId.toString());
        }

        return builder.compact();
    }

    public Optional<Claims> validateAndParseClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}