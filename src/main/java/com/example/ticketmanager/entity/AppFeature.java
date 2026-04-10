package com.example.ticketmanager.entity;

public enum AppFeature {
    DASHBOARD_ACCESS("Dashboard Access", "Open the dashboard overview."),
    DASHBOARD_MY_TICKET_STATUS("My Ticket Status Chart", "View personal ticket status distribution chart on dashboard."),
    DASHBOARD_ALL_TICKET_STATUS("All Ticket Status Chart", "View all-tickets status distribution chart on dashboard."),
    DASHBOARD_USER_COUNT("User Count Chart", "View user count by role chart on dashboard."),
    PROFILE_ACCESS("Profile Access", "View and update the user profile."),
    TICKETS_VIEW("Ticket Access", "View tickets assigned or visible to the user."),
    TICKETS_MANAGE("Ticket Management", "Create and edit tickets."),
    TICKETS_CREATE_STANDARD("Create Ticket", "Create standard tickets for internal support operations."),
    TICKETS_CREATE_VENDOR("Create Vendor Ticket", "Create vendor-owned tickets from vendor workspace."),
    TICKETS_CREATED_VIEW("My Created Tickets", "View tickets created by the current user."),
    TICKETS_REVIEW("Ticket Review", "Review resolved tickets and move them to closed or cancelled."),
    TICKETS_ALL_VIEW("All Tickets View", "View all tickets across the workspace."),
    SITE_VISIT_EDIT("Site Visit Edit", "Update site visit counters and log visit history."),
    CHAT_ACCESS("Chat Access", "Use the internal chat workspace."),
    NOTIFICATION_ACCESS("Notification Access", "View and manage system notifications."),
    ADMIN_ACCESS("Admin Management Access", "Access the admin management workspace and menus."),
    ADMIN_SUPPORT_TICKETS("Admin Support Tickets", "Review all support tickets from the admin area."),
    ADMIN_USER_MANAGEMENT("Admin User Management", "Manage registered users and account status."),
    ADMIN_ROLE_MANAGEMENT("Admin Role Management", "Create, edit, and delete roles."),
    ADMIN_ROLE_FEATURE_ASSIGNMENT("Admin Role Feature Assignment", "Assign features and permissions to roles."),
    ADMIN_EMAIL_NOTIFICATION_MANAGEMENT("Admin Email Notification Management", "Configure email notification settings and templates."),
    ADMIN_STAFF_BILLING("Admin Staff Billing", "Review assigned staff ticket billing summaries and invoices."),
    ADMIN_REPORT_ACCESS("Admin Report Access", "View system reports and analytics.");

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
