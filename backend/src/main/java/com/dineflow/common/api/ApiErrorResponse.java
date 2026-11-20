package com.dineflow.common.api;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response envelope when an API request fails or validation errors occur.
 */
public record ApiErrorResponse(
        boolean success,
        String errorCode,
        String message,
        List<ValidationErrorItem> errors,
        String path,
        Instant timestamp
) {
    public record ValidationErrorItem(String field, String message) {}

    public static ApiErrorResponse of(String errorCode, String message, String path) {
        return new ApiErrorResponse(false, errorCode, message, List.of(), path, Instant.now());
    }

    public static ApiErrorResponse ofValidation(String message, List<ValidationErrorItem> errors, String path) {
        return new ApiErrorResponse(false, "VALIDATION_FAILED", message, errors, path, Instant.now());
    }
}
