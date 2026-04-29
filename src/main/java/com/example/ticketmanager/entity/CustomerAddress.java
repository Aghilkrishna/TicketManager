package com.example.ticketmanager.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer_address")
public class CustomerAddress {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 150)
    private String customerName;

    @Column(length = 100)
    private String customerEmail;

    @Column(length = 20)
    private String customerPhone;

    @Column(length = 120)
    private String flat;

    @Column(length = 150)
    private String street;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(length = 20)
    private String pincode;

    @Column(length = 1000)
    private String locationLink;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private AppUser updatedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public CustomerAddress(String customerName, String customerEmail, String customerPhone, 
                          String flat, String street, String city, String state, String pincode, String locationLink) {
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.flat = flat;
        this.street = street;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.locationLink = locationLink;
    }

    public String getFullAddress() {
        StringBuilder address = new StringBuilder();
        if (flat != null && !flat.isBlank()) {
            address.append(flat).append(", ");
        }
        if (street != null && !street.isBlank()) {
            address.append(street).append(", ");
        }
        if (city != null && !city.isBlank()) {
            address.append(city).append(", ");
        }
        if (state != null && !state.isBlank()) {
            address.append(state).append(" ");
        }
        if (pincode != null && !pincode.isBlank()) {
            address.append(pincode);
        }
        return address.toString().trim();
    }

    @jakarta.persistence.PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @jakarta.persistence.PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
