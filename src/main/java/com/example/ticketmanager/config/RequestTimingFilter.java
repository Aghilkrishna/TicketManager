package com.example.ticketmanager.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class RequestTimingFilter extends OncePerRequestFilter {
    private static final long SLOW_REQUEST_THRESHOLD_MS = 750;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (durationMs >= SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("Slow request: {} {} completed with status {} in {} ms",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            }
        }
    }
}
