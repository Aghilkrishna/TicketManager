package com.example.ticketmanager.service;

import com.example.ticketmanager.config.AppProperties;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleReminderService {

    private static final List<TicketStatus> EXCLUDED_STATUSES =
            List.of(TicketStatus.CLOSED, TicketStatus.CANCELLED, TicketStatus.RESOLVED);

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
}
