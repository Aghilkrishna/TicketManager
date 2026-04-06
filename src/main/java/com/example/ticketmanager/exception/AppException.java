package com.example.ticketmanager.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Slf4j
public class AppException extends RuntimeException {
    private final HttpStatus status;

    public AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        log.debug("AppException created: status={}, message={}", status, message);
    }

    public AppException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        log.debug("AppException created: status={}, message={}, cause={}", status, message, cause.getClass().getSimpleName());
    }

    public HttpStatus getStatus() {
        return status;
    }
}
