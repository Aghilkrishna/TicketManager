package com.example.ticketmanager.service;

import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.Ticket;
import com.example.ticketmanager.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final TicketRepository ticketRepository;
    private final UserService userService;

    public record CustomerInfo(
            String name,
            String email,
            String phone,
            List<CustomerAddress> addresses
    ) {}

    public record CustomerAddress(
            String flat,
            String street,
            String city,
            String state,
            String pincode,
            String locationLink,
            String fullAddress,
            Long ticketId
    ) {}

    /**
     * Search for customers by mobile number or email
     * @param query Mobile number or email
     * @param username Current user username
     * @return List of matching customers
     */
    public List<CustomerInfo> searchCustomers(String query, String username) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        AppUser currentUser = userService.getByEmail(username);
        boolean isVendor = userService.hasRole(currentUser, "ROLE_VENDOR");

        // Search tickets by customer phone or email
        List<Ticket> tickets = ticketRepository.findAll().stream()
                .filter(ticket -> matchesQuery(ticket, query))
                .filter(ticket -> !isVendor || isVendorOwnTicket(ticket, currentUser.getId()))
                .collect(Collectors.toList());

        // Group by customer (phone + email combination)
        Map<String, List<Ticket>> customerGroups = tickets.stream()
                .collect(Collectors.groupingBy(this::getCustomerKey));

        return customerGroups.values().stream()
                .map(this::createCustomerInfo)
                .collect(Collectors.toList());
    }

    private boolean matchesQuery(Ticket ticket, String query) {
        String lowerQuery = query.toLowerCase().trim();
        return (ticket.getCustomerPhone() != null && ticket.getCustomerPhone().toLowerCase().contains(lowerQuery)) ||
               (ticket.getCustomerEmail() != null && ticket.getCustomerEmail().toLowerCase().contains(lowerQuery));
    }

    private boolean isVendorOwnTicket(Ticket ticket, Long vendorUserId) {
        return ticket.getVendorUser() != null && ticket.getVendorUser().getId().equals(vendorUserId);
    }

    private String getCustomerKey(Ticket ticket) {
        return (ticket.getCustomerPhone() != null ? ticket.getCustomerPhone() : "") + "|" +
               (ticket.getCustomerEmail() != null ? ticket.getCustomerEmail() : "");
    }

    private CustomerInfo createCustomerInfo(List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            return null;
        }

        Ticket firstTicket = tickets.get(0);
        
        // Collect unique addresses from all tickets
        Set<CustomerAddress> addresses = tickets.stream()
                .filter(this::hasAddress)
                .map(ticket -> new CustomerAddress(
                        ticket.getCustomerFlat(),
                        ticket.getCustomerStreet(),
                        ticket.getCustomerCity(),
                        ticket.getCustomerState(),
                        ticket.getCustomerPincode(),
                        ticket.getCustomerLocationLink(),
                        buildFullAddress(ticket),
                        ticket.getId()
                ))
                .collect(Collectors.toSet());

        return new CustomerInfo(
                firstTicket.getCustomerName(),
                firstTicket.getCustomerEmail(),
                firstTicket.getCustomerPhone(),
                new ArrayList<>(addresses)
        );
    }

    private boolean hasAddress(Ticket ticket) {
        return ticket.getCustomerFlat() != null || ticket.getCustomerStreet() != null ||
               ticket.getCustomerCity() != null || ticket.getCustomerState() != null ||
               ticket.getCustomerPincode() != null;
    }

    private String buildFullAddress(Ticket ticket) {
        StringBuilder address = new StringBuilder();
        if (ticket.getCustomerFlat() != null && !ticket.getCustomerFlat().isBlank()) {
            address.append(ticket.getCustomerFlat()).append(", ");
        }
        if (ticket.getCustomerStreet() != null && !ticket.getCustomerStreet().isBlank()) {
            address.append(ticket.getCustomerStreet()).append(", ");
        }
        if (ticket.getCustomerCity() != null && !ticket.getCustomerCity().isBlank()) {
            address.append(ticket.getCustomerCity()).append(", ");
        }
        if (ticket.getCustomerState() != null && !ticket.getCustomerState().isBlank()) {
            address.append(ticket.getCustomerState()).append(" ");
        }
        if (ticket.getCustomerPincode() != null && !ticket.getCustomerPincode().isBlank()) {
            address.append(ticket.getCustomerPincode());
        }
        return address.toString().trim();
    }
}
