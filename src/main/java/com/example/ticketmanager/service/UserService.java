package com.example.ticketmanager.service;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.PasswordResetToken;
import com.example.ticketmanager.entity.Role;
import com.example.ticketmanager.entity.RoleName;
import com.example.ticketmanager.exception.AppException;
import com.example.ticketmanager.repository.PasswordResetTokenRepository;
import com.example.ticketmanager.repository.RoleRepository;
import com.example.ticketmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final com.example.ticketmanager.config.AppProperties appProperties;

    public AppUser getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public AppUser getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public AuthDtos.ProfileResponse getProfile(String username) {
        AppUser user = getByUsername(username);
        return toProfile(user);
    }

    @Transactional
    public AuthDtos.ProfileResponse updateProfile(String username, AuthDtos.ProfileUpdateRequest request) {
        AppUser user = getByUsername(username);
        if (!user.getUsername().equals(request.username()) && userRepository.existsByUsername(request.username())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Username already in use");
        }
        if (!user.getEmail().equals(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Email already in use");
        }
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        return toProfile(userRepository.save(user));
    }

    public void createPasswordReset(String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Email not found"));
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(LocalDateTime.now().plusHours(2));
        passwordResetTokenRepository.save(token);
        String link = appProperties.baseUrl() + "/reset-password?token=" + token.getToken();
        emailService.send(user.getEmail(), "Password reset", "Reset your password using this link: " + link);
    }

    @Transactional
    public void resetPassword(AuthDtos.PasswordResetConfirmRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(request.token())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Invalid reset token"));
        if (token.isUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Reset token expired");
        }
        AppUser user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        token.setUsed(true);
    }

    public Set<String> getRoleNames(AppUser user) {
        return user.getRoles().stream().map(Role::getName).map(RoleName::name).collect(java.util.stream.Collectors.toSet());
    }

    public AuthDtos.ProfileResponse toProfile(AppUser user) {
        return new AuthDtos.ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.isEmailVerified(),
                getRoleNames(user)
        );
    }
}
