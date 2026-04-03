package com.example.ticketmanager.security;

import com.example.ticketmanager.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JwtService {
    private final AppProperties appProperties;
    private SecretKey secretKey;

    public JwtService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    void init() {
        this.secretKey = Keys.hmacShaKeyFor(appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        log.info("JWT service initialized with secret key length: {} bits", secretKey.getEncoded().length * 8);
        log.debug("JWT expiration time: {} ms", appProperties.jwt().expiration());
    }

    public String generateToken(AppUserPrincipal principal) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + appProperties.jwt().expiration());
        
        String token = Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid", principal.id())
                .claim("roles", principal.roleNames())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
        
        log.debug("JWT token generated for user: {}, expires: {}", 
                principal.getUsername(), expiration);
        return token;
    }

    public String extractUsername(String token) {
        String username = parse(token).getSubject();
        log.trace("Extracted username from JWT: {}", username);
        return username;
    }

    public AppUserPrincipal extractPrincipal(String token) {
        Claims claims = parse(token);
        Object rolesClaim = claims.get("roles");
        Set<String> roles = rolesClaim instanceof Iterable<?> iterable
                ? java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                .map(String::valueOf)
                .collect(Collectors.toSet())
                : Set.of();
        
        AppUserPrincipal principal = new AppUserPrincipal(
                claims.get("uid", Long.class),
                "",
                claims.getSubject(),
                "",
                true,
                roles
        );
        
        log.debug("Extracted principal from JWT: user={}, roles={}", 
                principal.getUsername(), roles);
        return principal;
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            log.trace("JWT token validation successful");
            return true;
        } catch (Exception ex) {
            log.debug("JWT token validation failed: {}", ex.getMessage());
            log.trace("JWT validation error", ex);
            return false;
        }
    }

    private Claims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        log.trace("JWT token parsed successfully, subject: {}, issued: {}, expires: {}", 
                claims.getSubject(), claims.getIssuedAt(), claims.getExpiration());
        return claims;
    }
}
