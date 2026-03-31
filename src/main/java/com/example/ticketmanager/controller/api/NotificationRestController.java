package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.service.NotificationService;
import com.example.ticketmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationRestController {
    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping
    public Object list(Principal principal) {
        return notificationService.viewUnreadForUser(userService.getByUsername(principal.getName()).getId());
    }

    @GetMapping("/count")
    public Object count(Principal principal) {
        return java.util.Map.of("count", notificationService.unreadCount(userService.getByUsername(principal.getName()).getId()));
    }
}
