package com.dineflow.common.api;

import java.time.Instant;

/**
 * Standard unified API response wrapper for DineFlow endpoints.
 * Ensures consistent JSON payloads across all REST controllers.
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, "Operation completed successfully");
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
