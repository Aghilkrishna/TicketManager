package com.example.ticketmanager.service;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.MobileVerificationToken;
import com.example.ticketmanager.exception.AppException;
import com.example.ticketmanager.repository.MobileVerificationTokenRepository;
import com.example.ticketmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MobileVerificationService {
    private final UserRepository userRepository;
    private final MobileVerificationTokenRepository mobileVerificationTokenRepository;
    private final SmsService smsService;

    @Transactional
    public AuthDtos.MobileVerificationResponse sendOtp(String email, String phone) {
        log.info("Sending OTP for mobile verification to user: {}", email);
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        // Normalize phone number
        String normalizedPhone = normalize(phone);
        log.debug("Normalized phone number: {}", normalizedPhone);
        
        // Verify the phone number belongs to the user
        if (!normalizedPhone.equals(normalize(user.getPhone()))) {
            log.warn("Phone number mismatch for user {}: requested={}, registered={}", 
                    email, normalizedPhone, normalize(user.getPhone()));
            throw new AppException(HttpStatus.BAD_REQUEST, "Phone number does not match user's registered number");
        }

        // Delete any existing tokens for this user
        mobileVerificationTokenRepository.deleteByUserId(user.getId());

        // Generate OTP
        String otp = smsService.generateOtp();
        log.debug("Generated OTP for user {}: {}", email, otp);

        // Create verification token
        MobileVerificationToken token = new MobileVerificationToken();
        token.setUser(user);
        token.setToken(otp);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.setCreatedAt(LocalDateTime.now());
        token.setUsed(false);
        mobileVerificationTokenRepository.save(token);

        // Send SMS
        boolean smsSent = smsService.sendOtp(normalizedPhone, otp);
        
        if (!smsSent) {
            log.error("Failed to send SMS OTP to user: {} for phone: {}", email, normalizedPhone);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send verification SMS");
        }

        log.info("OTP sent successfully to user: {} for phone: {}", email, normalizedPhone);
        return new AuthDtos.MobileVerificationResponse("OTP sent to your mobile number");
    }

    @Transactional
    public AuthDtos.MobileVerificationResponse verifyOtp(String email, String phone, String otp) {
        log.info("Verifying OTP for user: {}", email);
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        // Normalize phone number
        String normalizedPhone = normalize(phone);
        
        // Verify the phone number belongs to the user
        if (!normalizedPhone.equals(normalize(user.getPhone()))) {
            log.warn("Phone number mismatch during OTP verification for user {}: requested={}, registered={}", 
                    email, normalizedPhone, normalize(user.getPhone()));
            throw new AppException(HttpStatus.BAD_REQUEST, "Phone number does not match user's registered number");
        }

        // Find valid token
        MobileVerificationToken token = mobileVerificationTokenRepository.findByToken(otp)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP"));

        // Validate token
        if (!token.isValid()) {
            log.warn("Invalid or expired OTP used by user: {}", email);
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP");
        }

        // Verify token belongs to the user
        if (!token.getUser().getId().equals(user.getId())) {
            log.warn("OTP verification attempted by wrong user. Expected: {}, Actual: {}", 
                    token.getUser().getEmail(), email);
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }

        // Mark token as used
        token.setUsed(true);
        mobileVerificationTokenRepository.save(token);

        // Mark phone as verified
        user.setPhoneVerified(true);
        userRepository.save(user);

        log.info("Mobile number verified successfully for user: {}", email);
        return new AuthDtos.MobileVerificationResponse("Mobile number verified successfully");
    }

    private String normalize(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        // Remove spaces, dashes, parentheses and other formatting
        return phone.replaceAll("[\\s\\-\\(\\)]", "").trim();
    }
}
