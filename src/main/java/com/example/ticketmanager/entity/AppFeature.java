package com.example.ticketmanager.entity;

public enum AppFeature {
    DASHBOARD_ACCESS("Dashboard Access", "Open the dashboard overview."),
    PROFILE_ACCESS("Profile Access", "View and update the user profile."),
    TICKETS_VIEW("Ticket Access", "View tickets assigned or visible to the user."),
    TICKETS_MANAGE("Ticket Management", "Create and edit tickets."),
    SITE_VISIT_EDIT("Site Visit Edit", "Update site visit counters and log visit history."),
    CHAT_ACCESS("Chat Access", "Use the internal chat workspace."),
    ADMIN_SUPPORT_TICKETS("Admin Support Tickets", "Review all support tickets from the admin area."),
    ADMIN_USER_MANAGEMENT("Admin User Management", "Manage registered users and account status."),
    ADMIN_ROLE_MANAGEMENT("Admin Role Management", "Create, edit, and delete roles."),
    ADMIN_ROLE_FEATURE_ASSIGNMENT("Admin Role Feature Assignment", "Assign features and permissions to roles.");

    private final String label;
    private final String description;

    AppFeature(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String authority() {
        return "FEATURE_" + name();
    }
}
