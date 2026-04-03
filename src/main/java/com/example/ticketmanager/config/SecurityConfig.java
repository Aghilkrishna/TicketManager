package com.example.ticketmanager.config;

import com.example.ticketmanager.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring security filter chain");
        
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/ws/**"))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/register", "/vendor/login", "/vendor/register",
                                "/verify-email", "/reset-password", "/css/**", "/js/**",
                                "/api/auth/**", "/api/public/**", "/ws/**", "/access-denied", "/error").permitAll()
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.debug("Authentication failed for URI: {}", request.getRequestURI());
                            if (isApiRequest(request.getRequestURI(), request.getHeader("Accept"), request.getHeader("X-Requested-With"))) {
                                log.debug("Returning JSON error for API request");
                                writeJsonError(response, HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
                                return;
                            }
                            log.debug("Redirecting to login page for web request");
                            response.sendRedirect("/login");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.warn("Access denied for URI: {}, user: {}", 
                                    request.getRequestURI(), request.getUserPrincipal() != null ? 
                                    request.getUserPrincipal().getName() : "anonymous");
                            if (isApiRequest(request.getRequestURI(), request.getHeader("Accept"), request.getHeader("X-Requested-With"))) {
                                writeJsonError(response, HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
                                return;
                            }
                            response.sendRedirect("/access-denied");
                        }))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        
        log.info("Security filter chain configured successfully");
        return http.build();
    }

    @Bean
    AuthenticationProvider authenticationProvider() {
        log.debug("Creating authentication provider with BCrypt password encoder");
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        log.debug("Creating BCrypt password encoder");
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        log.debug("Creating authentication manager");
        return configuration.getAuthenticationManager();
    }

    private boolean isApiRequest(String uri, String acceptHeader, String requestedWith) {
        boolean isApi = (uri != null && uri.startsWith("/api/"))
                || (acceptHeader != null && acceptHeader.contains(MediaType.APPLICATION_JSON_VALUE))
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith);
        log.trace("Request type check: uri={}, isApi={}", uri, isApi);
        return isApi;
    }

    private void writeJsonError(jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String message) throws java.io.IOException {
        log.debug("Writing JSON error response: status={}, message={}", status, message);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
