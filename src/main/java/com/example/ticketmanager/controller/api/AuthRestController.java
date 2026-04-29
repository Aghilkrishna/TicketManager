package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.exception.AppException;
import com.example.ticketmanager.service.AuthService;
import com.example.ticketmanager.service.MobileVerificationService;
import com.example.ticketmanager.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRestController {
    private final AuthService authService;
    private final UserService userService;
    private final MobileVerificationService mobileVerificationService;

    @PostMapping("/register")
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/vendor/register")
    public AuthDtos.AuthResponse registerVendor(@Valid @RequestBody AuthDtos.VendorRegisterRequest request) {
        return authService.registerVendor(request);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletResponse response) {
        return authService.login(request, response);
    }

    @PostMapping("/vendor/login")
    public AuthDtos.AuthResponse vendorLogin(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletResponse response) {
        return authService.vendorLogin(request, response);
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        authService.logout(response);
    }

    @GetMapping("/verify")
    public String verify(@RequestParam String token) {
        authService.verifyEmail(token);
        return "Email verified";
    }

    @PostMapping("/password-reset")
    public String requestReset(@Valid @RequestBody AuthDtos.PasswordResetRequest request) {
        userService.createPasswordReset(request.email());
        return "Password reset email sent";
    }

    @PostMapping("/password-reset/confirm")
    public String confirmReset(@Valid @RequestBody AuthDtos.PasswordResetConfirmRequest request) {
        userService.resetPassword(request);
        return "Password reset successful";
    }

    @GetMapping("/me")
    public AuthDtos.ProfileResponse me(Principal principal) {
        if (principal == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userService.getProfile(principal.getName());
    }

    @PostMapping("/mobile/send-otp")
    public AuthDtos.MobileVerificationResponse sendMobileOtp(@Valid @RequestBody AuthDtos.MobileVerificationRequest request, Principal principal) {
        log.info("Mobile OTP send request received from user: {} for phone: {}", principal.getName(), request.phone());
        return mobileVerificationService.sendOtp(principal.getName(), request.phone());
    }

    @PostMapping("/mobile/verify-otp")
    public AuthDtos.MobileVerificationResponse verifyMobileOtp(@Valid @RequestBody AuthDtos.MobileOtpVerificationRequest request, Principal principal) {
        log.info("Mobile OTP verification request received from user: {} for phone: {}", principal.getName(), request.phone());
        return mobileVerificationService.verifyOtp(principal.getName(), request.phone(), request.otp());
    }
}
