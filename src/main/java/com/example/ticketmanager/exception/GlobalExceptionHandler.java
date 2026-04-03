package com.example.ticketmanager.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(AppException.class)
    public Object handleAppException(AppException ex, HttpServletRequest request) {
        log.warn("AppException handled: status={}, message={}, uri={}", 
                ex.getStatus(), ex.getMessage(), request.getRequestURI());
        return buildResponse(request, ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation exception: uri={}, errorCount={}", request.getRequestURI(), 
                ex.getBindingResult().getFieldErrors().size());
        
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Validation failed");
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
            log.debug("Validation error: field={}, message={}", error.getField(), error.getDefaultMessage());
        }
        body.put("errors", errors);
        if (isApiRequest(request)) {
            return ResponseEntity.badRequest().body(body);
        }
        return buildErrorPage(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: uri={}, message={}", request.getRequestURI(), ex.getMessage());
        return buildResponse(request, HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: uri={}, exception={}", request.getRequestURI(), ex.getClass().getSimpleName(), ex);
        return buildResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    private Object buildResponse(HttpServletRequest request, HttpStatus status, String message) {
        log.debug("Building error response: status={}, uri={}, isApi={}", 
                status, request.getRequestURI(), isApiRequest(request));
        
        if (!isApiRequest(request)) {
            return buildErrorPage(status, message, request.getRequestURI());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String acceptHeader = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        boolean isApi = (uri != null && uri.startsWith("/api/"))
                || (acceptHeader != null && acceptHeader.contains("application/json"))
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith);
        log.trace("Request type check: uri={}, isApi={}", uri, isApi);
        return isApi;
    }

    private ModelAndView buildErrorPage(HttpStatus status, String message, String path) {
        log.debug("Building error page: status={}, view={}, path={}", status, resolveViewName(status), path);
        
        ModelAndView modelAndView = new ModelAndView(resolveViewName(status));
        modelAndView.setStatus(status);
        modelAndView.addObject("status", status.value());
        modelAndView.addObject("errorTitle", status.getReasonPhrase());
        modelAndView.addObject("errorMessage", message);
        modelAndView.addObject("path", path);
        return modelAndView;
    }

    private String resolveViewName(HttpStatus status) {
        String viewName = switch (status) {
            case NOT_FOUND -> "error/404";
            case FORBIDDEN -> "error/403";
            case BAD_REQUEST -> "error/error";
            default -> "error/500";
        };
        log.trace("Resolved error view: status={}, view={}", status, viewName);
        return viewName;
    }
}
