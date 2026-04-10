package com.example.ticketmanager.service;

import com.example.ticketmanager.dto.AdminDtos;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.Ticket;
import com.example.ticketmanager.entity.TicketBillingStatus;
import com.example.ticketmanager.entity.TicketStatus;
import com.example.ticketmanager.exception.AppException;
import com.example.ticketmanager.repository.TicketRepository;
import com.example.ticketmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StaffBillingService {
    private static final Set<TicketStatus> BILLING_RELEVANT_STATUSES = EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);
    private static final Set<String> BILLABLE_ROLES = Set.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_AGENT");

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<AdminDtos.StaffBillingSummary> listStaffBillingSummaries() {
        Map<Long, BillingAccumulator> totalsByUser = new LinkedHashMap<>();

        List<AppUser> staffUsers = userRepository.findAll().stream()
                .filter(AppUser::isEnabled)
                .filter(this::isBillableStaff)
                .sorted(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (AppUser user : staffUsers) {
            totalsByUser.put(user.getId(), new BillingAccumulator(user));
        }

        for (Ticket ticket : ticketRepository.findByAssignedToIsNotNullAndStatusIn(BILLING_RELEVANT_STATUSES)) {
            AppUser assignedUser = ticket.getAssignedTo();
            if (assignedUser == null) {
                continue;
            }
            BillingAccumulator accumulator = totalsByUser.get(assignedUser.getId());
            if (accumulator == null) {
                continue;
            }
            accumulator.add(ticket);
        }

        return totalsByUser.values().stream()
                .map(BillingAccumulator::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminDtos.StaffBillingDetails getStaffBillingDetails(Long userId) {
        AppUser user = userService.getById(userId);
        if (!isBillableStaff(user)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Selected user is not available for staff billing");
        }

        // Fetch ALL resolved + closed tickets for accurate summary/status calculation
        List<Ticket> allTickets = ticketRepository.findByAssignedToIdAndStatusInOrderByUpdatedAtDesc(userId, BILLING_RELEVANT_STATUSES);
        BillingAccumulator accumulator = new BillingAccumulator(user);
        List<AdminDtos.StaffBillingTicketLine> lines = new ArrayList<>();
        for (Ticket ticket : allTickets) {
            accumulator.add(ticket);
            // Only include resolved tickets and UNPAID closed tickets in the visible list
            boolean isResolved = ticket.getStatus() == TicketStatus.RESOLVED;
            boolean isUnpaidClosed = ticket.getStatus() == TicketStatus.CLOSED
                    && ticket.getBillingStatus() != TicketBillingStatus.PAID;
            if (isResolved || isUnpaidClosed) {
                lines.add(new AdminDtos.StaffBillingTicketLine(
                        ticket.getId(),
                        ticket.getTitle(),
                        ticket.getStatus().name(),
                        resolveBillAmount(ticket),
                        toBillingStatusLabel(ticket.getBillingStatus()),
                        ticket.getUpdatedAt()
                ));
            }
        }

        // Unpaid closed figures are shown in the stat cards and totals
        long unpaidClosedCount = accumulator.closedCount - accumulator.paidClosedCount;
        BigDecimal unpaidClosedAmount = accumulator.closedAmount.subtract(accumulator.paidAmount);

        return new AdminDtos.StaffBillingDetails(
                user.getId(),
                displayName(user),
                user.getEmail(),
                user.getPhone(),
                roleLabels(user),
                accumulator.resolvedCount,
                accumulator.resolvedAmount,
                unpaidClosedCount,
                unpaidClosedAmount,
                unpaidClosedAmount,
                accumulator.paidAmount,
                unpaidClosedAmount,
                accumulator.billingStatusLabel(),
                lines
        );
    }

    @Transactional
    public void updateClosedTicketBillingStatus(Long userId, TicketBillingStatus status) {
        AppUser user = userService.getById(userId);
        if (!isBillableStaff(user)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Selected user is not available for staff billing");
        }
        List<Ticket> closedTickets = ticketRepository.findByAssignedToIdAndStatusOrderByUpdatedAtDesc(userId, TicketStatus.CLOSED);
        LocalDateTime paidAt = status == TicketBillingStatus.PAID ? LocalDateTime.now() : null;
        for (Ticket ticket : closedTickets) {
            ticket.setBillingStatus(status);
            ticket.setBillingPaidAt(paidAt);
        }
        ticketRepository.saveAll(closedTickets);
    }

    @Transactional
    public void updateSingleTicketBillingStatus(Long ticketId, TicketBillingStatus status) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Ticket not found"));
        if (ticket.getStatus() != TicketStatus.CLOSED) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only closed tickets can have their billing status updated");
        }
        AppUser assignedTo = ticket.getAssignedTo();
        if (assignedTo == null || !isBillableStaff(assignedTo)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Ticket is not assigned to billable staff");
        }
        ticket.setBillingStatus(status);
        ticket.setBillingPaidAt(status == TicketBillingStatus.PAID ? LocalDateTime.now() : null);
        ticketRepository.save(ticket);
    }

    private boolean isBillableStaff(AppUser user) {
        return user.getRoles().stream()
                .filter(role -> role.isActive())
                .map(role -> role.getName())
                .anyMatch(BILLABLE_ROLES::contains);
    }

    private Set<String> roleLabels(AppUser user) {
        return user.getRoles().stream()
                .filter(role -> role.isActive())
                .map(role -> userService.toRoleLabel(role.getName()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String displayName(AppUser user) {
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        if (firstName != null && !firstName.isBlank()) {
            return (firstName + " " + (lastName == null ? "" : lastName)).trim();
        }
        return user.getUsername();
    }

    private BigDecimal resolveBillAmount(Ticket ticket) {
        if (ticket.getActualCost() != null) {
            return ticket.getActualCost();
        }
        if (ticket.getEstimatedCost() != null) {
            return ticket.getEstimatedCost();
        }
        return BigDecimal.ZERO;
    }

    private String toBillingStatusLabel(TicketBillingStatus status) {
        return status == TicketBillingStatus.PAID ? "Paid" : "Unpaid";
    }

    private final class BillingAccumulator {
        private final AppUser user;
        private long resolvedCount;
        private BigDecimal resolvedAmount = BigDecimal.ZERO;
        private long closedCount;
        private BigDecimal closedAmount = BigDecimal.ZERO;
        private BigDecimal paidAmount = BigDecimal.ZERO;
        private long paidClosedCount;

        private BillingAccumulator(AppUser user) {
            this.user = user;
        }

        private void add(Ticket ticket) {
            BigDecimal amount = resolveBillAmount(ticket);
            if (ticket.getStatus() == TicketStatus.RESOLVED) {
                resolvedCount++;
                resolvedAmount = resolvedAmount.add(amount);
                return;
            }
            if (ticket.getStatus() == TicketStatus.CLOSED) {
                closedCount++;
                closedAmount = closedAmount.add(amount);
                if (ticket.getBillingStatus() == TicketBillingStatus.PAID) {
                    paidClosedCount++;
                    paidAmount = paidAmount.add(amount);
                }
            }
        }

        private String billingStatusLabel() {
            if (closedCount == 0) {
                return "No Closed Tickets";
            }
            if (paidClosedCount == 0) {
                return "Unpaid";
            }
            if (paidClosedCount == closedCount) {
                return "Paid";
            }
            return "Partially Paid";
        }

        private AdminDtos.StaffBillingSummary toSummary() {
            // Only show unpaid closed tickets in the billing list counts/amounts
            long unpaidClosedCount = closedCount - paidClosedCount;
            BigDecimal unpaidClosedAmount = closedAmount.subtract(paidAmount);
            return new AdminDtos.StaffBillingSummary(
                    user.getId(),
                    displayName(user),
                    user.getEmail(),
                    roleLabels(user),
                    resolvedCount,
                    resolvedAmount,
                    unpaidClosedCount,
                    unpaidClosedAmount,
                    billingStatusLabel()
            );
        }
    }
}

