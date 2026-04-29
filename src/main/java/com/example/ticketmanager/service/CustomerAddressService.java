package com.example.ticketmanager.service;

import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.CustomerAddress;
import com.example.ticketmanager.repository.CustomerAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerAddressService {

    private final CustomerAddressRepository customerAddressRepository;
    private final UserService userService;

    public record AddressInfo(
            Long id,
            String flat,
            String street,
            String city,
            String state,
            String pincode,
            String locationLink,
            String fullAddress
    ) {}

    /**
     * Search for customer addresses by email or phone with access restrictions
     */
    public List<AddressInfo> searchCustomerAddresses(String email, String phone, String currentUsername) {
        AppUser currentUser = userService.getByEmail(currentUsername);
        boolean isVendor = userService.hasRole(currentUser, "ROLE_VENDOR");
        
        List<CustomerAddress> addresses;
        if (isVendor) {
            // Vendor can only access addresses they created
            addresses = customerAddressRepository.findByCustomerEmailOrPhoneWithAccess(email, phone, currentUser.getId());
        } else {
            // Admin and other roles can access all addresses
            addresses = customerAddressRepository.findByCustomerEmailOrPhone(email, phone);
        }
        
        return addresses.stream()
                .map(this::toAddressInfo)
                .toList();
    }

    /**
     * Get customer addresses by both email and phone with access restrictions
     */
    public List<AddressInfo> getCustomerAddresses(String email, String phone, String currentUsername) {
        AppUser currentUser = userService.getByEmail(currentUsername);
        boolean isVendor = userService.hasRole(currentUser, "ROLE_VENDOR");
        
        List<CustomerAddress> addresses;
        if (isVendor) {
            addresses = customerAddressRepository.findByCustomerEmailAndPhoneWithAccess(email, phone, currentUser.getId());
        } else {
            addresses = customerAddressRepository.findByCustomerEmailAndPhone(email, phone);
        }
        
        return addresses.stream()
                .map(this::toAddressInfo)
                .toList();
    }

    /**
     * Create a new customer address
     */
    @Transactional
    public CustomerAddress createCustomerAddress(String customerName, String customerEmail, String customerPhone,
                                               String flat, String street, String city, String state, 
                                               String pincode, String locationLink, String currentUsername) {
        AppUser currentUser = userService.getByEmail(currentUsername);
        
        CustomerAddress address = new CustomerAddress(
                customerName, customerEmail, customerPhone, flat, street, city, state, pincode, locationLink
        );
        address.setCreatedBy(currentUser);
        address.setUpdatedBy(currentUser);
        
        return customerAddressRepository.save(address);
    }

    /**
     * Get address by ID with access restrictions
     */
    public Optional<CustomerAddress> getAddressById(Long addressId, String currentUsername) {
        AppUser currentUser = userService.getByEmail(currentUsername);
        boolean isVendor = userService.hasRole(currentUser, "ROLE_VENDOR");
        
        if (isVendor) {
            return customerAddressRepository.findByIdWithAccess(addressId, currentUser.getId());
        } else {
            return customerAddressRepository.findById(addressId);
        }
    }

    /**
     * Update an existing customer address
     */
    @Transactional
    public CustomerAddress updateCustomerAddress(Long addressId, String customerName, String customerEmail, 
                                               String customerPhone, String flat, String street, String city, 
                                               String state, String pincode, String locationLink, 
                                               String currentUsername) {
        AppUser currentUser = userService.getByEmail(currentUsername);
        
        CustomerAddress address = getAddressById(addressId, currentUsername)
                .orElseThrow(() -> new RuntimeException("Address not found or access denied"));
        
        address.setCustomerName(customerName);
        address.setCustomerEmail(customerEmail);
        address.setCustomerPhone(customerPhone);
        address.setFlat(flat);
        address.setStreet(street);
        address.setCity(city);
        address.setState(state);
        address.setPincode(pincode);
        address.setLocationLink(locationLink);
        address.setUpdatedBy(currentUser);
        
        return customerAddressRepository.save(address);
    }

    private AddressInfo toAddressInfo(CustomerAddress address) {
        return new AddressInfo(
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
