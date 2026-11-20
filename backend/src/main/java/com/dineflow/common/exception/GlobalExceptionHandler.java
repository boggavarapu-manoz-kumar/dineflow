package com.dineflow.common.exception;

import com.dineflow.common.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business rule violation: {} - Code: {}", ex.getMessage(), ex.getErrorCode());
        ApiErrorResponse response = ApiErrorResponse.of(
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiErrorResponse.ValidationErrorItem> errorItems = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errorItems.add(new ApiErrorResponse.ValidationErrorItem(fieldError.getField(), fieldError.getDefaultMessage()));
        }

        log.warn("Validation failure on URI [{}]: {}", request.getRequestURI(), errorItems);
        ApiErrorResponse response = ApiErrorResponse.ofValidation(
                "Request validation failed",
                errorItems,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled server exception processing path [{}]: ", request.getRequestURI(), ex);
        ApiErrorResponse response = ApiErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                "An unexpected server error occurred. Please contact DineFlow support.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
