package com.example.ticketmanager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String AUTH_COOKIE = "TM_TOKEN";

    private final JwtService jwtService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return false;
        }
        return uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/images/")
                || uri.startsWith("/webjars/")
                || uri.startsWith("/api/auth/login")
                || uri.startsWith("/api/auth/register")
                || uri.startsWith("/api/auth/vendor")
                || uri.startsWith("/api/auth/password-reset")
                || uri.startsWith("/api/auth/verify")
                || uri.startsWith("/api/auth/mobile")
                || uri.startsWith("/api/public/")
                || uri.equals("/")
                || uri.equals("/login")
                || uri.equals("/register")
                || uri.equals("/vendor/login")
                || uri.equals("/vendor/register")
                || uri.equals("/verify-email")
                || uri.equals("/reset-password")
                || uri.startsWith("/ws/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        
        if (token != null) {
            log.trace("JWT token found in request: {}", request.getRequestURI());
            
            if (jwtService.isValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                // The JWT is signed with the server secret and is not expired.
                // It already carries the complete authority set (ROLE_xxx + FEATURE_xxx),
                // so no database round-trip is needed to authenticate the request.
                AppUserPrincipal principal = jwtService.extractPrincipal(token);
                log.debug("JWT authentication successful for user: {}, uri: {}",
                        principal.getUsername(), request.getRequestURI());

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                log.debug("Invalid JWT token or user already authenticated: {}", request.getRequestURI());
            }
        } else {
            log.trace("No JWT token found in request: {}", request.getRequestURI());
        }
        
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        // Try Authorization header first
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            log.trace("JWT token resolved from Authorization header");
            return token;
        }
        
        // Try cookie
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            log.trace("No cookies found in request");
            return null;
        }
        
        Optional<Cookie> cookie = Arrays.stream(cookies)
                .filter(item -> AUTH_COOKIE.equals(item.getName()))
                .findFirst();
        
        if (cookie.isPresent()) {
            log.trace("JWT token resolved from cookie: {}", AUTH_COOKIE);
            return cookie.get().getValue();
        }
        
        log.trace("No JWT token found in headers or cookies");
        return null;
    }
}
