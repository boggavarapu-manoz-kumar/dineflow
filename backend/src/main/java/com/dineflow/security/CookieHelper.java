package com.dineflow.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieHelper {

    private final boolean secureCookie;
    private final String sameSite;

    public CookieHelper(
            @Value("${dineflow.security.cookie.secure:true}") boolean secureCookie,
            @Value("${dineflow.security.cookie.same-site:Strict}") String sameSite) {
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken, long maxAgeSeconds) {
        return ResponseCookie.from("__Host-DineFlow-RT", refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/api/v1/auth")
                .maxAge(maxAgeSeconds)
                .sameSite(sameSite)
                .build();
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return ResponseCookie.from("__Host-DineFlow-RT", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/api/v1/auth")
                .maxAge(0)
                .sameSite(sameSite)
                .build();
    }
}