package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.service.CustomerAddressService;
import com.example.ticketmanager.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerRestController {

    private final CustomerService customerService;
    private final CustomerAddressService customerAddressService;

    @PreAuthorize("hasAuthority('FEATURE_TICKETS_CREATE_VENDOR') or hasAuthority('FEATURE_TICKETS_CREATE_STANDARD') or hasAuthority('FEATURE_TICKETS_MANAGE')")
    @GetMapping("/search")
    public List<CustomerService.CustomerInfo> searchCustomers(
            @RequestParam String query,
            Principal principal) {
        return customerService.searchCustomers(query, principal.getName());
    }

    @PreAuthorize("hasAuthority('FEATURE_TICKETS_CREATE_VENDOR') or hasAuthority('FEATURE_TICKETS_CREATE_STANDARD') or hasAuthority('FEATURE_TICKETS_MANAGE')")
    @GetMapping("/addresses")
    public List<CustomerAddressService.AddressInfo> getCustomerAddresses(
            @RequestParam String email,
            @RequestParam(required = false) String phone,
            Principal principal) {
        return customerAddressService.getCustomerAddresses(email, phone, principal.getName());
    }

    @PreAuthorize("hasAuthority('FEATURE_TICKETS_CREATE_VENDOR') or hasAuthority('FEATURE_TICKETS_CREATE_STANDARD') or hasAuthority('FEATURE_TICKETS_MANAGE')")
    @PostMapping("/addresses")
    public CustomerAddressService.AddressInfo createCustomerAddress(
            @RequestParam String customerName,
            @RequestParam String customerEmail,
            @RequestParam String customerPhone,
            @RequestParam String flat,
            @RequestParam String street,
            @RequestParam String city,
            @RequestParam String state,
            @RequestParam String pincode,
            @RequestParam(required = false) String locationLink,
            Principal principal) {
        var address = customerAddressService.createCustomerAddress(
                customerName, customerEmail, customerPhone, flat, street, city, state, pincode, locationLink, principal.getName());
        return new CustomerAddressService.AddressInfo(
                address.getId(),
                address.getFlat(),
                address.getStreet(),
                address.getCity(),
                address.getState(),
                address.getPincode(),
                address.getLocationLink(),
                address.getFullAddress()
        );
    }
}
