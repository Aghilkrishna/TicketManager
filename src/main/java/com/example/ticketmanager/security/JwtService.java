package com.example.ticketmanager.security;

import com.example.ticketmanager.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

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
    }

    public String generateToken(AppUserPrincipal principal) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + appProperties.jwt().expiration());
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid", principal.id())
                .claim("roles", principal.roleNames())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public AppUserPrincipal extractPrincipal(String token) {
        Claims claims = parse(token);
        Object rolesClaim = claims.get("roles");
        Set<String> roles = rolesClaim instanceof Iterable<?> iterable
                ? java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                .map(String::valueOf)
                .collect(Collectors.toSet())
                : Set.of();
        return new AppUserPrincipal(
                claims.get("uid", Long.class),
                claims.getSubject(),
                "",
                true,
                roles
        );
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
