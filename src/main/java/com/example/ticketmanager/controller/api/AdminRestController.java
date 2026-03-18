package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.repository.UserRepository;
import com.example.ticketmanager.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRestController {
    private final TicketService ticketService;
    private final UserRepository userRepository;

    @GetMapping("/report")
    public Map<String, Object> report() {
        return Map.of(
                "ticketCount", ticketService.countAll(),
                "userCount", userRepository.count()
        );
    }
}
