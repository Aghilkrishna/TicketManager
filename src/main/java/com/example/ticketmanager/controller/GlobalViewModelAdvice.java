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

    @ModelAttribute("currentRoles")
    public Set<String> currentRoles(Principal principal) {
        if (principal == null) {
            return Set.of();
        }
        return userService.getProfile(principal.getName()).roles();
    }
}
