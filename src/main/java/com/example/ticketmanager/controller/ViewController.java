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
    @PreAuthorize("hasAuthority('FEATURE_DASHBOARD_ACCESS')")
    public String dashboard(Model model, Principal principal) {
        model.addAttribute("profile", userService.getProfile(principal.getName()));
        return "dashboard";
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('FEATURE_PROFILE_ACCESS')")
    public String profile(Model model, Principal principal) {
        var profile = userService.getProfile(principal.getName());
        model.addAttribute("profile", profile);
        model.addAttribute("profileRoleLabels", profile.roles().stream().map(userService::toRoleLabel).toList());
        return "profile";
    }

    @GetMapping("/tickets")
    @PreAuthorize("hasAuthority('FEATURE_TICKETS_VIEW')")
    public String tickets(Model model, Principal principal) {
        boolean vendor = userService.hasRole(principal.getName(), "ROLE_VENDOR");
        model.addAttribute("ticketTab", "open");
        model.addAttribute("ticketPageTitle", vendor ? "My Tickets" : "My Open Tickets");
        model.addAttribute("ticketSubtitle", vendor ? "Tickets created by you and available for updates" : "Open tickets currently assigned to you");
        model.addAttribute("forcedStatus", "OPEN");
        model.addAttribute("assignedOnly", !vendor);
        return "tickets";
    }

    @GetMapping("/tickets/closed")
    @PreAuthorize("hasAuthority('FEATURE_TICKETS_VIEW')")
    public String closedTickets(Model model, Principal principal) {
        boolean vendor = userService.hasRole(principal.getName(), "ROLE_VENDOR");
        model.addAttribute("ticketTab", "closed");
        model.addAttribute("ticketPageTitle", vendor ? "My Closed Tickets" : "Closed Tickets");
        model.addAttribute("ticketSubtitle", vendor ? "Closed tickets created by you" : "Closed tickets assigned to you");
        model.addAttribute("forcedStatus", "CLOSED");
        model.addAttribute("assignedOnly", !vendor);
        return "tickets";
    }

    @GetMapping("/tickets/view")
    @PreAuthorize("hasAuthority('FEATURE_TICKETS_VIEW')")
    public String viewTicket(@RequestParam Long id, Model model, Principal principal) {
        boolean adminScope = userService.hasRole(principal.getName(), "ROLE_ADMIN") || userService.hasRole(principal.getName(), "ROLE_MANAGER");
        model.addAttribute("ticket", ticketService.get(id, principal.getName(), adminScope));
        return "ticket-view";
    }

    @PreAuthorize("hasAuthority('FEATURE_TICKETS_MANAGE')")
    @GetMapping("/tickets/create")
    public String createTicket() {
        return "ticket-create";
    }

    @PreAuthorize("hasAnyAuthority('FEATURE_TICKETS_MANAGE','FEATURE_SITE_VISIT_EDIT')")
    @GetMapping("/tickets/edit")
    public String editTicket(@RequestParam Long id, Model model, Principal principal) {
        boolean adminScope = userService.hasRole(principal.getName(), "ROLE_ADMIN") || userService.hasRole(principal.getName(), "ROLE_MANAGER");
        model.addAttribute("ticket", ticketService.get(id, principal.getName(), adminScope));
        return "ticket-edit";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public String admin() {
        return "redirect:/admin/support-tickets";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "error/403";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/support-tickets")
    public String adminSupportTickets() {
        return "admin-support-tickets";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/users")
    public String adminUsers() {
        return "admin-users";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/roles")
    public String adminRoles() {
        return "admin-roles";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/role-features")
    public String adminRoleFeatures() {
        return "admin-role-features";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/email-notifications")
    public String adminEmailNotifications() {
        return "admin-email-notifications";
    }

    @GetMapping("/chat")
    @PreAuthorize("hasAuthority('FEATURE_CHAT_ACCESS')")
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
