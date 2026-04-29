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
    private final CustomerAddressService customerAddressService;

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
                .map(ticketList -> createCustomerInfo(ticketList, username))
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

    private CustomerInfo createCustomerInfo(List<Ticket> tickets, String username) {
        if (tickets.isEmpty()) {
            return null;
        }

        Ticket firstTicket = tickets.get(0);
        
        // Get addresses from the customer_address table using the service
        List<CustomerAddressService.AddressInfo> addressInfos = customerAddressService.getCustomerAddresses(
                firstTicket.getCustomerEmail(), 
                firstTicket.getCustomerPhone(), 
                username
        );
        
        // Convert AddressInfo to CustomerAddress records
        List<CustomerAddress> addresses = addressInfos.stream()
                .map(addressInfo -> new CustomerAddress(
                        addressInfo.flat(),
                        addressInfo.street(),
                        addressInfo.city(),
                        addressInfo.state(),
                        addressInfo.pincode(),
                        addressInfo.locationLink(),
                        addressInfo.fullAddress(),
                        null // ticketId is not needed for customer search display
                ))
                .collect(Collectors.toList());

        return new CustomerInfo(
                firstTicket.getCustomerName(),
                firstTicket.getCustomerEmail(),
                firstTicket.getCustomerPhone(),
                addresses
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
