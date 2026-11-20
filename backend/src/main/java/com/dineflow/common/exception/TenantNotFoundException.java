package com.dineflow.common.exception;

import org.springframework.http.HttpStatus;

public class TenantNotFoundException extends BusinessException {

    public TenantNotFoundException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "TENANT_RESOLUTION_FAILED");
    }
}
