package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.service.AuthService;
import com.example.ticketmanager.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRestController {
    private final AuthService authService;
    private final UserService userService;

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
        return userService.getProfile(principal.getName());
    }
}
