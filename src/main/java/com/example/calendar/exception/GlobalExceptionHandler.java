package com.example.calendar.exception;

import com.example.calendar.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(ApiValidationException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(ApiConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ApiConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse("CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(ApiForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(ApiForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiErrorResponse("FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(ApiNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ApiNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse("NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        String message = exception.getMessage() == null ? "Unexpected server error." : exception.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse("INTERNAL_ERROR", message));
    }
}
