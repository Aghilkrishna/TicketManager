package com.example.ticketmanager.entity;

public enum TicketServiceType {
    LEADS("Leads"),
    INSTALLATION("Installation"),
    SERVICE("Service"),
    AMC("AMC"),
    SITE_VISIT("Site Visit"),
    REPAIR("Repair"),
    MAINTENANCE("AMC");

    private final String label;

    TicketServiceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
