package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.entity.TicketStatus;
import com.example.ticketmanager.repository.TicketRepository;
import com.example.ticketmanager.repository.UserRepository;
import com.example.ticketmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardRestController {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * "My Ticket Status" chart data.
     * - Vendor role: tickets created by the user.
     * - Admin / Manager / Agent roles: tickets assigned to the user.
     */
    @PreAuthorize("hasAuthority('FEATURE_DASHBOARD_MY_TICKET_STATUS')")
    @GetMapping("/my-ticket-status")
    public Map<String, Long> myTicketStatus(Principal principal) {
        com.example.ticketmanager.entity.AppUser user = userService.getByEmail(principal.getName());
        boolean isVendor = userService.hasRole(user, "ROLE_VENDOR");  // use loaded user, not email string

        List<Object[]> rows = isVendor
                ? ticketRepository.countCreatedByStatus(user.getId())
                : ticketRepository.countAssignedByStatus(user.getId());

        return buildStatusMap(rows);
    }

    /**
     * "All Ticket Status" chart data – all tickets across the workspace.
     * Visible to Admin and Manager only.
     */
    @PreAuthorize("hasAuthority('FEATURE_DASHBOARD_ALL_TICKET_STATUS')")
    @GetMapping("/all-ticket-status")
    public Map<String, Long> allTicketStatus() {
        return buildStatusMap(ticketRepository.countAllByStatus());
    }

    /**
     * "User Count" chart data – number of users per role.
     * Visible to Admin only.
     */
    @PreAuthorize("hasAuthority('FEATURE_DASHBOARD_USER_COUNT')")
    @GetMapping("/user-count")
    public Map<String, Long> userCount() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : userRepository.countUsersByRole()) {
            String roleName = ((String) row[0]).replace("ROLE_", "");
            // Capitalise first letter, lowercase rest  e.g. ADMIN → Admin
            String label = roleName.charAt(0) + roleName.substring(1).toLowerCase();
            result.merge(label, ((Number) row[1]).longValue(), Long::sum);
        }
        return result;
    }

    @PreAuthorize("hasAuthority('FEATURE_DASHBOARD_ACCESS')")
    @GetMapping("/metrics")
    public Map<String, Object> metrics(Principal principal) {
        var user = userService.getByEmail(principal.getName());
        boolean vendor = userService.hasRole(user, "ROLE_VENDOR");
        boolean allTicketScope = userService.hasAuthority(user, "FEATURE_DASHBOARD_ALL_TICKET_STATUS");

        List<Object[]> rows = allTicketScope
                ? ticketRepository.countAllByStatus()
                : (vendor ? ticketRepository.countCreatedByStatus(user.getId()) : ticketRepository.countAssignedByStatus(user.getId()));

        Map<String, Long> statusCounts = buildStatusMap(rows);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCounts", statusCounts);
        result.put("totalTickets", statusCounts.values().stream().mapToLong(Long::longValue).sum());
        result.put("activeUsers", vendor ? null : userRepository.countEnabledUsersByActiveRoleNames(List.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_AGENT")));
        result.put("activeVendors", vendor ? null : userRepository.countEnabledUsersByActiveRoleNames(List.of("ROLE_VENDOR")));
        result.put("scope", allTicketScope ? "all" : "mine");
        result.put("visibleCards", vendor
                ? Set.of("enquiry", "open", "inProgress", "onHold", "resolved", "closed", "cancelled", "totalTickets")
                : Set.of("enquiry", "open", "inProgress", "onHold", "resolved", "closed", "cancelled", "totalTickets", "activeUsers", "activeVendors"));
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Map<String, Long> buildStatusMap(List<Object[]> rows) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : rows) {
            TicketStatus status = (TicketStatus) row[0];
            long count = ((Number) row[1]).longValue();
            byStatus.put(status.name(), count);
        }
        // Ensure every declared enum status is represented (even with 0)
        Map<String, Long> result = new LinkedHashMap<>();
        for (TicketStatus s : TicketStatus.values()) {
            result.put(s.name(), byStatus.getOrDefault(s.name(), 0L));
        }
        // Keep any extra status keys from data (defensive for future customizations)
        for (Map.Entry<String, Long> entry : byStatus.entrySet()) {
            result.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
