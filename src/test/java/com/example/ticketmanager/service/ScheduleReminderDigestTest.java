package com.example.ticketmanager.service;

import com.example.ticketmanager.config.AppProperties;
import com.example.ticketmanager.entity.*;
import com.example.ticketmanager.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleReminderDigestTest {

    @Mock TicketRepository ticketRepository;
    @Mock EmailService emailService;
    @Mock EmailNotificationSettingsService notificationSettingsService;
    @Mock UserService userService;
    @Mock AppProperties appProperties;

    @InjectMocks ScheduleReminderService service;

    private AppProperties.ScheduleReminder enabledReminder;

    @BeforeEach
    void setup() {
        enabledReminder = new AppProperties.ScheduleReminder(true, "0 0 18 * * ?", "0 0 9 * * ?", "0 0 6 * * ?");
        when(appProperties.scheduleReminder()).thenReturn(enabledReminder);
    }

    @Test
    void userDigest_skipsWhenNotificationDisabled() {
        when(notificationSettingsService.isEmailEnabled(EmailNotificationAction.OPEN_TICKETS_DAILY_REMINDER))
                .thenReturn(false);
        service.sendDailyUserOpenTicketsDigest();
        verifyNoInteractions(ticketRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    void userDigest_skipsWhenNoActiveUsers() {
        when(notificationSettingsService.isEmailEnabled(EmailNotificationAction.OPEN_TICKETS_DAILY_REMINDER))
                .thenReturn(true);
        when(ticketRepository.findDistinctAssignedUsersWithActiveTickets(anyList()))
                .thenReturn(List.of());
        service.sendDailyUserOpenTicketsDigest();
        verifyNoInteractions(emailService);
    }

    @Test
    void userDigest_sendsOneEmailPerUser() {
        when(notificationSettingsService.isEmailEnabled(EmailNotificationAction.OPEN_TICKETS_DAILY_REMINDER))
                .thenReturn(true);
        AppUser user1 = makeUser(1L, "alice@example.com", "Alice", "Smith");
        AppUser user2 = makeUser(2L, "bob@example.com", "Bob", "Jones");
        when(ticketRepository.findDistinctAssignedUsersWithActiveTickets(anyList()))
                .thenReturn(List.of(user1, user2));
        when(ticketRepository.findByAssignedToIdAndStatusInOrderByUpdatedAtDesc(eq(1L), anyList()))
                .thenReturn(List.of(makeTicket(101L, "Fix router", TicketStatus.OPEN, TicketPriority.HIGH, user1)));
        when(ticketRepository.findByAssignedToIdAndStatusInOrderByUpdatedAtDesc(eq(2L), anyList()))
                .thenReturn(List.of(makeTicket(102L, "Check cables", TicketStatus.ON_HOLD, TicketPriority.MEDIUM, user2)));
        service.sendDailyUserOpenTicketsDigest();
        verify(emailService, times(1)).sendOpenTicketsDigest(eq(user1), anyList(), anyString());
        verify(emailService, times(1)).sendOpenTicketsDigest(eq(user2), anyList(), anyString());
    }

    @Test
    void userDigest_rowsMappedCorrectly() {
        when(notificationSettingsService.isEmailEnabled(EmailNotificationAction.OPEN_TICKETS_DAILY_REMINDER))
                .thenReturn(true);
        AppUser user = makeUser(1L, "alice@example.com", "Alice", "Smith");
        when(ticketRepository.findDistinctAssignedUsersWithActiveTickets(anyList())).thenReturn(List.of(user));
        Ticket ticket = makeTicket(101L, "Fix router", TicketStatus.ON_HOLD, TicketPriority.HIGH, user);
        ticket.setCustomerName("Acme Corp");
        when(ticketRepository.findByAssignedToIdAndStatusInOrderByUpdatedAtDesc(eq(1L), anyList()))
                .thenReturn(List.of(ticket));
        ArgumentCaptor<List<EmailService.TicketDigestRow>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        service.sendDailyUserOpenTicketsDigest();
        verify(emailService).sendOpenTicketsDigest(eq(user), rowsCaptor.capture(), anyString());
        List<EmailService.TicketDigestRow> rows = rowsCaptor.getValue();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).id()).isEqualTo(101L);
        assertThat(rows.get(0).statusLabel()).isEqualTo("On Hold");
        assertThat(rows.get(0).statusColor()).isEqualTo("#d97706");
        assertThat(rows.get(0).priorityLabel()).isEqualTo("High");
        assertThat(rows.get(0).customerName()).isEqualTo("Acme Corp");
    }

    @Test
    void adminDigest_skipsWhenNotificationDisabled() {
        when(notificationSettingsService.isEmailEnabled(EmailNotificationAction.FOLLOWUP_ADMIN_DAILY_REMINDER))
                .thenReturn(false);
        service.sendDailyAdminFollowupDigest();
        verifyNoInteractions(ticketRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    void adminDigest_skipsWhenNoFollowupTickets() {
        when(notificationSettingsService.isEmailEnabled(EmailNotificationAction.FOLLOWUP_ADMIN_DAILY_REMINDER))
                .thenReturn(true);
        when(ticketRepository.findByStatusInOrderByUpdatedAtDesc(anyList())).thenReturn(List.of());
        service.sendDailyAdminFollowupDigest();
        verifyNoInteractions(emailService);
    }

    @Test
    void adminDigest_sendsOneEmailToAllAdmins() {
        when(notificationSettingsService.isEmailEnabled(EmailNotificationAction.FOLLOWUP_ADMIN_DAILY_REMINDER))
                .thenReturn(true);
        AppUser assignee = makeUser(5L, "agent@example.com", "Agent", "One");
        when(ticketRepository.findByStatusInOrderByUpdatedAtDesc(anyList()))
                .thenReturn(List.of(makeTicket(200L, "Follow up client", TicketStatus.FOLLOW_UP, TicketPriority.MEDIUM, assignee)));
        when(userService.getAdminEmails()).thenReturn(List.of("admin1@example.com", "admin2@example.com"));
        service.sendDailyAdminFollowupDigest();
        ArgumentCaptor<List<String>> emailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(emailService, times(1)).sendFollowupAdminDigest(emailsCaptor.capture(), anyList(), anyString());
        assertThat(emailsCaptor.getValue()).containsExactly("admin1@example.com", "admin2@example.com");
    }

    @Test
    void adminDigest_skipsWhenNoAdminEmails() {
        when(notificationSettingsService.isEmailEnabled(EmailNotificationAction.FOLLOWUP_ADMIN_DAILY_REMINDER))
                .thenReturn(true);
        AppUser assignee = makeUser(5L, "agent@example.com", "Agent", "One");
        when(ticketRepository.findByStatusInOrderByUpdatedAtDesc(anyList()))
                .thenReturn(List.of(makeTicket(200L, "Follow up", TicketStatus.SITE_REVISIT, TicketPriority.LOW, assignee)));
        when(userService.getAdminEmails()).thenReturn(List.of());
        service.sendDailyAdminFollowupDigest();
        verifyNoInteractions(emailService);
    }

    private AppUser makeUser(Long id, String email, String firstName, String lastName) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setEmail(email);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setUsername(firstName.toLowerCase());
        return u;
    }

    private Ticket makeTicket(Long id, String title, TicketStatus status, TicketPriority priority, AppUser assignedTo) {
        Ticket t = new Ticket();
        t.setId(id);
        t.setTitle(title);
        t.setStatus(status);
        t.setPriority(priority);
        t.setAssignedTo(assignedTo);
        return t;
    }
}
