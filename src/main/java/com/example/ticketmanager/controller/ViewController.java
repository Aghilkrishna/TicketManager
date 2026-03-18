package com.example.ticketmanager.controller;

import com.example.ticketmanager.service.TicketService;
import com.example.ticketmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ViewController {
    private final UserService userService;
    private final TicketService ticketService;

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        model.addAttribute("profile", userService.getProfile(principal.getName()));
        return "dashboard";
    }

    @GetMapping("/profile")
    public String profile(Model model, Principal principal) {
        model.addAttribute("profile", userService.getProfile(principal.getName()));
        return "profile";
    }

    @GetMapping("/tickets")
    public String tickets(Model model) {
        model.addAttribute("ticketTab", "open");
        model.addAttribute("ticketPageTitle", "My Open Tickets");
        model.addAttribute("ticketSubtitle", "Open tickets currently assigned to you");
        model.addAttribute("forcedStatus", "OPEN");
        return "tickets";
    }

    @GetMapping("/tickets/closed")
    public String closedTickets(Model model) {
        model.addAttribute("ticketTab", "closed");
        model.addAttribute("ticketPageTitle", "Closed Tickets");
        model.addAttribute("ticketSubtitle", "Closed tickets assigned to you");
        model.addAttribute("forcedStatus", "CLOSED");
        return "tickets";
    }

    @GetMapping("/tickets/view")
    public String viewTicket(@RequestParam Long id, Model model, Principal principal) {
        boolean adminScope = userService.getProfile(principal.getName()).roles().contains("ROLE_ADMIN");
        model.addAttribute("ticket", ticketService.get(id, principal.getName(), adminScope));
        return "ticket-view";
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @GetMapping("/tickets/create")
    public String createTicket() {
        return "ticket-create";
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @GetMapping("/tickets/edit")
    public String editTicket(@RequestParam Long id, Model model, Principal principal) {
        model.addAttribute("ticket", ticketService.get(id, principal.getName(), true));
        return "ticket-edit";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }

    @GetMapping("/chat")
    public String chat() {
        return "chat";
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        return "verify-email";
    }

    @GetMapping("/reset-password")
    public String resetPassword(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        return "reset-password";
    }
}
