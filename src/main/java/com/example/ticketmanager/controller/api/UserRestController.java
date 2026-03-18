package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.repository.UserRepository;
import com.example.ticketmanager.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserRestController {
    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping("/profile")
    public AuthDtos.ProfileResponse profile(Principal principal) {
        return userService.getProfile(principal.getName());
    }

    @PatchMapping("/profile")
    public AuthDtos.ProfileResponse updateProfile(Principal principal, @Valid @RequestBody AuthDtos.ProfileUpdateRequest request) {
        return userService.updateProfile(principal.getName(), request);
    }

    @GetMapping("/search")
    public Object search(@RequestParam String query) {
        return userRepository.findTop10ByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCase(
                query, query, query
        ).stream().map(user -> Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "phone", user.getPhone()
        )).toList();
    }
}
