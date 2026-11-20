package com.dineflow.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProperties {

    @Value("${dineflow.security.jwt.secret:dineflow-default-ultra-secure-hmac-sha-256-secret-key-32-bytes!}")
    private String secret;

    @Value("${dineflow.security.jwt.access-token-expiration-seconds:900}")
    private long accessTokenExpirationSeconds;

    @Value("${dineflow.security.jwt.refresh-token-expiration-seconds:604800}")
    private long refreshTokenExpirationSeconds;

    @Value("${dineflow.security.jwt.issuer:dineflow-auth-server}")
    private String issuer;

    public String getSecret() {
        return secret;
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationSeconds;
    }

    public String getIssuer() {
        return issuer;
    }
}