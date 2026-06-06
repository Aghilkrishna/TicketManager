package com.example.ticketmanager.service;

import com.example.ticketmanager.config.AppProperties;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.EmailNotificationAction;
import com.example.ticketmanager.entity.Ticket;
import com.example.ticketmanager.entity.TicketStatus;
import com.example.ticketmanager.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleReminderService {

    private static final List<TicketStatus> EXCLUDED_STATUSES =
            List.of(TicketStatus.CLOSED, TicketStatus.CANCELLED, TicketStatus.RESOLVED);

    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(
            TicketStatus.LEADS, TicketStatus.OPEN, TicketStatus.SITE_VISITED,
            TicketStatus.IN_PROGRESS, TicketStatus.ON_HOLD, TicketStatus.FOLLOW_UP,
            TicketStatus.SITE_REVISIT, TicketStatus.QUOTED
    );

    private static final List<TicketStatus> FOLLOWUP_STATUSES = List.of(
            TicketStatus.FOLLOW_UP, TicketStatus.SITE_REVISIT
    );

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final TicketRepository ticketRepository;
    private final EmailService emailService;
    private final EmailNotificationSettingsService notificationSettingsService;
    private final UserService userService;
    private final AppProperties appProperties;

    @Scheduled(cron = "${app.schedule-reminder.day-before-cron:0 0 18 * * ?}")
    public void sendDayBeforeReminders() {
        if (appProperties.scheduleReminder() == null || !appProperties.scheduleReminder().enabled()) {
            log.info("Schedule reminder disabled. Skipping day-before reminders.");
            return;
        }
        if (!notificationSettingsService.isEmailEnabled(EmailNotificationAction.SCHEDULE_REMINDER_DAY_BEFORE)) {
            log.info("SCHEDULE_REMINDER_DAY_BEFORE email disabled in notification settings.");
            return;
        }

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime start = tomorrow.atStartOfDay();
        LocalDateTime end = tomorrow.atTime(23, 59, 59);

        List<Ticket> tickets = ticketRepository.findScheduledTicketsForReminder(start, end, EXCLUDED_STATUSES);
        List<String> adminEmails = userService.getAdminEmails();

        log.info("Day-before reminder: {} ticket(s) scheduled for {}. Admin CC emails: {}", tickets.size(), tomorrow, adminEmails);

        for (Ticket ticket : tickets) {
            try {
                emailService.sendScheduleReminderEmail(ticket, ticket.getAssignedTo(), adminEmails, "DAY_BEFORE");
                log.debug("Day-before reminder sent for ticket #{} to {}", ticket.getId(), ticket.getAssignedTo().getEmail());
            } catch (Exception e) {
                log.error("Failed to send day-before reminder for ticket #{}", ticket.getId(), e);
            }
        }
    }

    @Scheduled(cron = "${app.schedule-reminder.on-day-cron:0 0 9 * * ?}")
    public void sendOnDayReminders() {
        if (appProperties.scheduleReminder() == null || !appProperties.scheduleReminder().enabled()) {
            log.info("Schedule reminder disabled. Skipping on-day reminders.");
            return;
        }
        if (!notificationSettingsService.isEmailEnabled(EmailNotificationAction.SCHEDULE_REMINDER_ON_DAY)) {
            log.info("SCHEDULE_REMINDER_ON_DAY email disabled in notification settings.");
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(23, 59, 59);

        List<Ticket> tickets = ticketRepository.findScheduledTicketsForReminder(start, end, EXCLUDED_STATUSES);
        List<String> adminEmails = userService.getAdminEmails();

        log.info("On-day reminder: {} ticket(s) scheduled for {}. Admin CC emails: {}", tickets.size(), today, adminEmails);

        for (Ticket ticket : tickets) {
            try {
                emailService.sendScheduleReminderEmail(ticket, ticket.getAssignedTo(), adminEmails, "ON_DAY");
                log.debug("On-day reminder sent for ticket #{} to {}", ticket.getId(), ticket.getAssignedTo().getEmail());
            } catch (Exception e) {
                log.error("Failed to send on-day reminder for ticket #{}", ticket.getId(), e);
            }
        }
    }

    @Scheduled(cron = "${app.schedule-reminder.daily-digest-cron:0 0 6 * * ?}")
    public void sendDailyUserOpenTicketsDigest() {
        if (appProperties.scheduleReminder() == null || !appProperties.scheduleReminder().enabled()) {
            log.info("Schedule reminder disabled. Skipping daily user open tickets digest.");
            return;
        }
        if (!notificationSettingsService.isEmailEnabled(EmailNotificationAction.OPEN_TICKETS_DAILY_REMINDER)) {
            log.info("OPEN_TICKETS_DAILY_REMINDER email disabled in notification settings.");
            return;
        }

        String today = LocalDate.now().format(DISPLAY_DATE_FMT);
        List<AppUser> users = ticketRepository.findDistinctAssignedUsersWithActiveTickets(ACTIVE_STATUSES);
        log.info("Daily user digest: {} user(s) with active tickets.", users.size());

        for (AppUser user : users) {
            try {
                List<Ticket> userTickets = ticketRepository
                        .findByAssignedToIdAndStatusInOrderByUpdatedAtDesc(user.getId(), ACTIVE_STATUSES);
                if (userTickets.isEmpty()) continue;
                List<EmailService.TicketDigestRow> rows = userTickets.stream().map(this::toDigestRow).toList();
                emailService.sendOpenTicketsDigest(user, rows, today);
                log.debug("Open tickets digest sent for user {} ({} tickets)", user.getEmail(), rows.size());
            } catch (Exception e) {
                log.error("Failed to send open tickets digest for user {}", user.getEmail(), e);
            }
        }
    }

    @Scheduled(cron = "${app.schedule-reminder.daily-digest-cron:0 0 6 * * ?}")
    public void sendDailyAdminFollowupDigest() {
        if (appProperties.scheduleReminder() == null || !appProperties.scheduleReminder().enabled()) {
            log.info("Schedule reminder disabled. Skipping daily admin follow-up digest.");
            return;
        }
        if (!notificationSettingsService.isEmailEnabled(EmailNotificationAction.FOLLOWUP_ADMIN_DAILY_REMINDER)) {
            log.info("FOLLOWUP_ADMIN_DAILY_REMINDER email disabled in notification settings.");
            return;
        }

        List<Ticket> tickets = ticketRepository.findByStatusInOrderByUpdatedAtDesc(FOLLOWUP_STATUSES);
        if (tickets.isEmpty()) {
            log.info("Admin follow-up digest: no follow-up/site-revisit tickets. Skipping.");
            return;
        }

        List<String> adminEmails = userService.getAdminEmails();
        if (adminEmails.isEmpty()) {
            log.warn("Admin follow-up digest: no admin email addresses found. Skipping.");
            return;
        }

        String today = LocalDate.now().format(DISPLAY_DATE_FMT);
        List<EmailService.TicketDigestRow> rows = tickets.stream().map(this::toDigestRow).toList();
        try {
            emailService.sendFollowupAdminDigest(adminEmails, rows, today);
            log.info("Admin follow-up digest sent to {} admin(s) with {} ticket(s)", adminEmails.size(), rows.size());
        } catch (Exception e) {
            log.error("Failed to send admin follow-up digest", e);
        }
    }

    private EmailService.TicketDigestRow toDigestRow(Ticket ticket) {
        String statusLabel = switch (ticket.getStatus()) {
            case LEADS -> "Leads";
            case OPEN -> "Open";
            case SITE_VISITED -> "Site Visited";
            case IN_PROGRESS -> "In Progress";
            case ON_HOLD -> "On Hold";
            case FOLLOW_UP -> "Follow Up";
            case SITE_REVISIT -> "Site Revisit";
            case QUOTED -> "Quoted";
            case RESOLVED -> "Resolved";
            case CLOSED -> "Closed";
            case CANCELLED -> "Cancelled";
        };
        String statusColor = switch (ticket.getStatus()) {
            case LEADS -> "#6d28d9";
            case OPEN -> "#16a34a";
            case SITE_VISITED -> "#0ea5e9";
            case IN_PROGRESS -> "#0f6cbd";
            case ON_HOLD -> "#d97706";
            case FOLLOW_UP -> "#ea580c";
            case SITE_REVISIT -> "#dc2626";
            case QUOTED -> "#059669";
            case RESOLVED, CLOSED -> "#6b7280";
            case CANCELLED -> "#9ca3af";
        };
        String priorityLabel = switch (ticket.getPriority()) {
            case LOW -> "Low";
            case MEDIUM -> "Medium";
            case HIGH -> "High";
            case CRITICAL -> "Critical";
        };
        AppUser assignedTo = ticket.getAssignedTo();
        String assignedToName = "Unassigned";
        if (assignedTo != null) {
            String fn = assignedTo.getFirstName();
            assignedToName = (fn != null && !fn.isBlank())
                    ? (fn + " " + (assignedTo.getLastName() != null ? assignedTo.getLastName() : "")).trim()
                    : assignedTo.getUsername();
        }
        String customerName = ticket.getCustomerName() != null ? ticket.getCustomerName() : "—";
        String updatedAt = ticket.getUpdatedAt() != null
                ? ticket.getUpdatedAt().format(DATE_FMT)
                : "—";
        String ticketUrl = appProperties.baseUrl() + "/tickets/view?id=" + ticket.getId();
        return new EmailService.TicketDigestRow(
                ticket.getId(), ticket.getTitle(), statusLabel, statusColor,
                priorityLabel, assignedToName, customerName, updatedAt, ticketUrl
        );
    }
}
