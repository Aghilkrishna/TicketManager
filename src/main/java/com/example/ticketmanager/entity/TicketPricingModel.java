package com.example.ticketmanager.entity;

public enum TicketPricingModel {
    FIXED_PRICE("Fixed Price"),
    HOURLY_RATE("Hourly Rate");

    private final String label;

    TicketPricingModel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
