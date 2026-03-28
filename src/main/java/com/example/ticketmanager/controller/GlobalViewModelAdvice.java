package com.example.ticketmanager.controller;

import com.example.ticketmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.Set;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalViewModelAdvice {
    private final UserService userService;

    @ModelAttribute("currentUsername")
    public String currentUsername(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userService.getProfile(principal.getName()).username();
    }

    @ModelAttribute("currentDisplayName")
    public String currentDisplayName(Principal principal) {
        if (principal == null) {
            return null;
        }
        var profile = userService.getProfile(principal.getName());
        String firstName = profile.firstName();
        String lastName = profile.lastName();
        if (firstName != null && !firstName.isBlank()) {
            return (firstName + " " + (lastName == null ? "" : lastName)).trim();
        }
        return profile.username();
    }

    @ModelAttribute("currentHasProfileImage")
    public boolean currentHasProfileImage(Principal principal) {
        if (principal == null) {
            return false;
        }
        return userService.getProfile(principal.getName()).profileImageUploaded();
    }

    @ModelAttribute("currentRoles")
    public Set<String> currentRoles(Principal principal) {
        if (principal == null) {
            return Set.of();
        }
        return userService.getProfile(principal.getName()).roles();
    }

    @ModelAttribute("currentFeatures")
    public Set<String> currentFeatures(Principal principal) {
        if (principal == null) {
            return Set.of();
        }
        return userService.getFeatureAuthorities(userService.getByUsername(principal.getName()));
    }
}
