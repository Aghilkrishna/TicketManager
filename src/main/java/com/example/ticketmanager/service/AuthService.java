package com.example.ticketmanager.service;

import com.example.ticketmanager.config.AppProperties;
import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.EmailNotificationAction;
import com.example.ticketmanager.entity.EmailVerificationToken;
import com.example.ticketmanager.entity.Role;
import com.example.ticketmanager.exception.AppException;
import com.example.ticketmanager.repository.EmailVerificationTokenRepository;
import com.example.ticketmanager.repository.RoleRepository;
import com.example.ticketmanager.repository.UserRepository;
import com.example.ticketmanager.security.AppUserPrincipal;
import com.example.ticketmanager.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.example.ticketmanager.security.JwtAuthenticationFilter.AUTH_COOKIE;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final EmailService emailService;
    private final AppProperties appProperties;
    private final EmailNotificationSettingsService emailNotificationSettingsService;

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        validateUniqueCredentials(request.username(), request.email(), request.phone());
        Role defaultRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Default role missing"));
        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPhone(normalize(request.phone()));
        user.setPassword(passwordEncoder.encode(request.password()));
        user.getRoles().add(defaultRole);
        userRepository.save(user);
        sendVerificationEmail(user);
        return new AuthDtos.AuthResponse(user.getId(), user.getUsername(), user.getEmail(), Set.of("ROLE_USER"),
                "Registration successful. Verify your email to activate your account.");
    }

    @Transactional
    public AuthDtos.AuthResponse registerVendor(AuthDtos.VendorRegisterRequest request) {
        validateUniqueCredentials(request.username(), request.email(), request.phone());
        Role vendorRole = roleRepository.findByName("ROLE_VENDOR")
                .orElseThrow(() -> new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Vendor role missing"));
        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPhone(normalize(request.phone()));
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setCompanyName(normalize(request.companyName()));
        user.setContactPerson(normalize(request.contactPerson()));
        user.setGstNumber(normalize(request.gstNumber()));
        user.setFlat(normalize(request.flat()));
        user.setBuilding(normalize(request.building()));
        user.setArea(normalize(request.area()));
        user.setCity(normalize(request.city()));
        user.setState(normalize(request.state()));
        user.setCountry(normalize(request.country()));
        user.setPincode(normalize(request.pincode()));
        user.getRoles().add(vendorRole);
        userRepository.save(user);
        sendVerificationEmail(user);
        return new AuthDtos.AuthResponse(user.getId(), user.getUsername(), user.getEmail(), Set.of("ROLE_VENDOR"),
                "Vendor registration successful. Verify your email to activate your account.");
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, HttpServletResponse response) {
        return login(request, response, false);
    }

    public AuthDtos.AuthResponse vendorLogin(AuthDtos.LoginRequest request, HttpServletResponse response) {
        return login(request, response, true);
    }

    private AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, HttpServletResponse response, boolean vendorPortal) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        AppUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        boolean vendorUser = user.getRoles().stream().filter(Role::isActive).anyMatch(role -> "ROLE_VENDOR".equals(role.getName()));
        if (vendorPortal && !vendorUser) {
            throw new AppException(HttpStatus.FORBIDDEN, "This account must use the regular login page.");
        }
        if (!vendorPortal && vendorUser) {
            throw new AppException(HttpStatus.FORBIDDEN, "Vendor users must sign in from the vendor login page.");
        }
        if (!user.isEmailVerified()) {
            throw new AppException(HttpStatus.FORBIDDEN, "Verify your email before logging in");
        }
        String token = jwtService.generateToken(new AppUserPrincipal(user));
        Cookie cookie = new Cookie(AUTH_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (appProperties.jwt().expiration() / 1000));
        response.addCookie(cookie);
        return new AuthDtos.AuthResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles().stream().filter(Role::isActive).map(Role::getName).collect(Collectors.toSet()),
                "Login successful"
        );
    }

    public void logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(AUTH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    @Transactional
    public void verifyEmail(String tokenValue) {
        EmailVerificationToken token = emailVerificationTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Invalid verification token"));
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Verification token expired");
        }
        token.setUsed(true);
        token.getUser().setEmailVerified(true);
    }

    private void sendVerificationEmail(AppUser user) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(LocalDateTime.now().plusHours(24));
        emailVerificationTokenRepository.save(token);
        String link = appProperties.baseUrl() + "/verify-email?token=" + token.getToken();
        if (emailNotificationSettingsService.isEnabled(EmailNotificationAction.ACCOUNT_VERIFICATION)) {
            emailService.sendVerificationEmail(user, link);
        }
    }

    private void validateUniqueCredentials(String username, String email, String phone) {
        if (userRepository.existsByUsername(username)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Email already exists");
        }
        String normalizedPhone = normalize(phone);
        if (normalizedPhone != null && userRepository.existsByPhone(normalizedPhone)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Phone number already exists");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
