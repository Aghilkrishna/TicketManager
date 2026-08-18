package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.entity.AppUser;
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

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardRestController {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * Organization-wide ticket counts by status.
     * Includes all tickets in the system (no user assignment filter).
     * Also returns counts of active technicians and vendors.
     * Visible to ROLE_ADMIN only.
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/org-metrics")
    public Map<String, Object> orgMetrics() {
        Map<String, Long> statusCounts = buildStatusMap(ticketRepository.countAllByStatus());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCounts", statusCounts);
        result.put("totalTickets", statusCounts.values().stream().mapToLong(Long::longValue).sum());
        result.put("technicianCount", userRepository.countEnabledUsersByActiveRoleNames(List.of("ROLE_AGENT")));
        result.put("vendorCount", userRepository.countEnabledUsersByActiveRoleNames(List.of("ROLE_VENDOR")));
        return result;
    }

    /**
     * Current user's ticket counts by status (tickets assigned to them).
     * Visible to all roles with FEATURE_DASHBOARD_ACCESS.
     */
    @PreAuthorize("hasAuthority('FEATURE_DASHBOARD_ACCESS')")
    @GetMapping("/user-metrics")
    public Map<String, Object> userMetrics(Principal principal) {
        AppUser user = userService.getByEmail(principal.getName());
        Map<String, Long> statusCounts = buildStatusMap(ticketRepository.countAssignedByStatus(user.getId()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCounts", statusCounts);
        result.put("totalTickets", statusCounts.values().stream().mapToLong(Long::longValue).sum());
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
        // Ensure every declared enum status is present (zero-filled if missing)
        Map<String, Long> result = new LinkedHashMap<>();
        for (TicketStatus s : TicketStatus.values()) {
            result.put(s.name(), byStatus.getOrDefault(s.name(), 0L));
        }
        return result;
    }
}
