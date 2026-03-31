package com.example.ticketmanager.entity;

public enum EmailNotificationAction {
    ACCOUNT_VERIFICATION("Account Verification", "Send verification emails after user registration."),
    PASSWORD_RESET("Password Reset", "Send forgot-password and password reset link emails."),
    TICKET_CREATED("Ticket Created", "Notify relevant users when a new ticket is created."),
    TICKET_UPDATED("Ticket Updated", "Notify relevant users when ticket details are updated."),
    COMMENT_ADDED("Comment Added", "Notify relevant users when a new ticket comment or reply is added."),
    SITE_VISIT_ADDED("Site Visit Added", "Notify relevant users when a site visit entry is recorded."),
    CHAT_MESSAGE("Chat Message", "Notify users when they receive a new chat message.");

    private final String label;
    private final String description;

    EmailNotificationAction(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
