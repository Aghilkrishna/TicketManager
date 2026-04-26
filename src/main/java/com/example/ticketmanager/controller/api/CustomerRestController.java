package com.example.ticketmanager.controller.api;

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

    @PreAuthorize("hasAuthority('FEATURE_TICKETS_CREATE_VENDOR') or hasAuthority('FEATURE_TICKETS_CREATE_STANDARD') or hasAuthority('FEATURE_TICKETS_MANAGE')")
    @GetMapping("/search")
    public List<CustomerService.CustomerInfo> searchCustomers(
            @RequestParam String query,
            Principal principal) {
        return customerService.searchCustomers(query, principal.getName());
    }
}
