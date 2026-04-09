package com.example.ticketmanager.controller;

import com.example.ticketmanager.service.TicketService;
import com.example.ticketmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/vendor/login")
    public String vendorLogin() {
        return "vendor-login";
    }

    @GetMapping("/vendor/register")
    public String vendorRegister() {
        return "vendor-register";
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('FEATURE_DASHBOARD_ACCESS')")
    public String dashboard(Model model, Principal principal) {
        model.addAttribute("profile", userService.getProfile(principal.getName()));
        return "dashboard";
    }

    @GetMapping("/admin/users/{id}")
    @PreAuthorize("hasAuthority('FEATURE_ADMIN_USER_MANAGEMENT')")
    public String adminUserDetails(@PathVariable Long id, Model model) {
        model.addAttribute("userDetails", userService.getUserDetails(id));
        model.addAttribute("idProofs", userService.getUserIdProofs(id));
        return "admin-user-details";
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('FEATURE_PROFILE_ACCESS')")
    public String profile(Model model, Principal principal) {
        var profile = userService.getProfile(principal.getName());
        model.addAttribute("profile", profile);
        model.addAttribute("profileRoleLabels", profile.roles().stream().map(userService::toRoleLabel).toList());
        
        // Add ID proof documents for Vendor and Agent roles
        boolean isVendorOrAgent = userService.hasRole(principal.getName(), "ROLE_VENDOR") || 
                                 userService.hasRole(principal.getName(), "ROLE_AGENT");
        if (isVendorOrAgent) {
            var userIdProofs = userService.getUserIdProofs(profile.id());
            model.addAttribute("userIdProofs", userIdProofs);
        }
        model.addAttribute("isVendorOrAgent", isVendorOrAgent);
        
        return "profile";
    }

    @GetMapping("/tickets")
    @PreAuthorize("hasAuthority('FEATURE_TICKETS_VIEW')")
    public String tickets() {
        return "redirect:/tickets/pending";
    }

    @GetMapping("/tickets/pending")
    @PreAuthorize("hasAuthority('FEATURE_TICKETS_VIEW')")
    public String pendingTickets(Model model) {
        model.addAttribute("ticketTab", "pending");
        model.addAttribute("ticketPageTitle", "My Pending Tickets");
        model.addAttribute("ticketSubtitle", "Open, in-progress, and on-hold tickets assigned to you");
        model.addAttribute("forcedStatuses", java.util.List.of("OPEN", "IN_PROGRESS", "ON_HOLD"));
        model.addAttribute("assignedOnly", true);
        model.addAttribute("adminScope", false);
        return "tickets";
    }

    @GetMapping("/tickets/resolved")
    @PreAuthorize("hasAuthority('FEATURE_TICKETS_VIEW')")
    public String resolvedTickets(Model model) {
        model.addAttribute("ticketTab", "resolved");
        model.addAttribute("ticketPageTitle", "My Resolved Tickets");
        model.addAttribute("ticketSubtitle", "Resolved, closed, and cancelled tickets assigned to you");
        model.addAttribute("forcedStatuses", java.util.List.of("RESOLVED", "CLOSED", "CANCELLED"));
        model.addAttribute("assignedOnly", true);
        model.addAttribute("adminScope", false);
        return "tickets";
    }

    @GetMapping("/tickets/review")
    @PreAuthorize("hasAuthority('FEATURE_TICKETS_REVIEW')")
    public String reviewTickets(Model model) {
        model.addAttribute("ticketTab", "review");
        model.addAttribute("ticketPageTitle", "My Review Tickets");
        model.addAttribute("ticketSubtitle", "Resolved tickets ready for review and closure decisions");
        model.addAttribute("forcedStatuses", java.util.List.of("RESOLVED"));
        model.addAttribute("assignedOnly", false);
        model.addAttribute("adminScope", true);
        return "tickets";
    }

    @GetMapping("/tickets/all")
    @PreAuthorize("hasAuthority('FEATURE_TICKETS_ALL_VIEW')")
    public String allTickets(Model model) {
        model.addAttribute("ticketTab", "all");
        model.addAttribute("ticketPageTitle", "All Tickets");
        model.addAttribute("ticketSubtitle", "All tickets across the workspace");
        model.addAttribute("forcedStatuses", java.util.List.of());
        model.addAttribute("assignedOnly", false);
        model.addAttribute("adminScope", true);
        return "tickets";
    }

    @GetMapping("/tickets/view")
    @PreAuthorize("hasAuthority('FEATURE_TICKETS_VIEW')")
    public String viewTicket(@RequestParam Long id, Model model, Principal principal) {
        boolean adminScope = ticketService.canManageAllTickets(principal.getName());
        model.addAttribute("ticket", ticketService.get(id, principal.getName(), adminScope));
        return "ticket-view";
    }

    @PreAuthorize("hasAnyAuthority('FEATURE_TICKETS_MANAGE','FEATURE_TICKETS_CREATE_STANDARD','FEATURE_TICKETS_CREATE_VENDOR')")
    @GetMapping("/tickets/create")
    public String createTicket() {
        return "ticket-create";
    }

    @PreAuthorize("hasAnyAuthority('FEATURE_TICKETS_MANAGE','FEATURE_SITE_VISIT_EDIT')")
    @GetMapping("/tickets/edit")
    public String editTicket(@RequestParam Long id, Model model, Principal principal) {
        boolean adminScope = ticketService.canManageAllTickets(principal.getName());
        var ticket = ticketService.get(id, principal.getName(), adminScope);
        
        // Prevent agent and vendor users from editing closed tickets
        if (!adminScope && "CLOSED".equals(ticket.status()) && 
            (userService.hasRole(principal.getName(), "ROLE_AGENT") || userService.hasRole(principal.getName(), "ROLE_VENDOR"))) {
            throw new org.springframework.security.access.AccessDeniedException("Agent and vendor users cannot edit closed tickets");
        }
        
        model.addAttribute("ticket", ticket);
        return "ticket-edit";
    }

    @PreAuthorize("hasAnyAuthority('FEATURE_ADMIN_ACCESS','FEATURE_ADMIN_SUPPORT_TICKETS','FEATURE_ADMIN_USER_MANAGEMENT','FEATURE_ADMIN_ROLE_MANAGEMENT','FEATURE_ADMIN_ROLE_FEATURE_ASSIGNMENT','FEATURE_ADMIN_EMAIL_NOTIFICATION_MANAGEMENT')")
    @GetMapping("/admin")
    public String admin() {
        return "redirect:/admin/support-tickets";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "error/403";
    }

    @PreAuthorize("hasAuthority('FEATURE_ADMIN_SUPPORT_TICKETS')")
    @GetMapping("/admin/support-tickets")
    public String adminSupportTickets() {
        return "admin-support-tickets";
    }

    @PreAuthorize("hasAuthority('FEATURE_ADMIN_USER_MANAGEMENT')")
    @GetMapping("/admin/users")
    public String adminUsers() {
        return "admin-users";
    }

    @PreAuthorize("hasAuthority('FEATURE_ADMIN_ROLE_MANAGEMENT')")
    @GetMapping("/admin/roles")
    public String adminRoles() {
        return "admin-roles";
    }

    @PreAuthorize("hasAuthority('FEATURE_ADMIN_ROLE_FEATURE_ASSIGNMENT')")
    @GetMapping("/admin/role-features")
    public String adminRoleFeatures() {
        return "admin-role-features";
    }

    @PreAuthorize("hasAuthority('FEATURE_ADMIN_EMAIL_NOTIFICATION_MANAGEMENT')")
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
