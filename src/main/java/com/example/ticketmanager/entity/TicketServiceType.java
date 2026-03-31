package com.example.ticketmanager.entity;

public enum TicketServiceType {
    INSTALLATION("Installation"),
    REPAIR("Repair"),
    MAINTENANCE("Maintenance");

    private final String label;

    TicketServiceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
