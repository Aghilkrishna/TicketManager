# Ticket Digest Reminder Emails — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two daily 6 AM digest reminder emails — one per assigned user listing their active tickets, one per admin org covering all follow-up/site-revisit tickets.

**Architecture:** Extend `ScheduleReminderService` with two new `@Scheduled` methods sharing a `dailyDigestCron` (default `0 0 6 * * ?`). `EmailService` grows two send methods and a `TicketDigestRow` record. Two Thymeleaf templates render the table emails. Two new `EmailNotificationAction` enum values allow independent enable/disable from the admin settings page.

**Tech Stack:** Spring Boot, Spring Mail (`JavaMailSender`/`MimeMessageHelper`), Thymeleaf, Spring Data JPA, Lombok

---

## File Map

| Action | File |
|---|---|
| Modify | `src/main/java/com/example/ticketmanager/entity/EmailNotificationAction.java` |
| Modify | `src/main/java/com/example/ticketmanager/config/DataInitializer.java` |
| Modify | `src/main/java/com/example/ticketmanager/config/AppProperties.java` |
| Modify | `src/main/resources/application.yml` |
| Modify | `src/main/java/com/example/ticketmanager/repository/TicketRepository.java` |
| Modify | `src/main/java/com/example/ticketmanager/service/EmailService.java` |
| Create | `src/main/resources/templates/email/open-tickets-digest.html` |
| Create | `src/main/resources/templates/email/followup-admin-digest.html` |
| Modify | `src/main/java/com/example/ticketmanager/service/ScheduleReminderService.java` |
| Create | `src/test/java/com/example/ticketmanager/service/ScheduleReminderDigestTest.java` |

---

## Task 1: Add enum values + seed defaults

**Files:**
- Modify: `src/main/java/com/example/ticketmanager/entity/EmailNotificationAction.java`
- Modify: `src/main/java/com/example/ticketmanager/config/DataInitializer.java`

- [ ] **Step 1: Add two new enum values at the end of `EmailNotificationAction`**

In `EmailNotificationAction.java`, after `SCHEDULE_REMINDER_ON_DAY`, add:

```java
    SCHEDULE_REMINDER_ON_DAY("On-Day Schedule Reminder", "Send reminder email to assigned users at 9 AM on the ticket's scheduled date."),
    OPEN_TICKETS_DAILY_REMINDER("Open Tickets Daily Reminder", "Send daily digest to each user listing all their active tickets at 6 AM."),
    FOLLOWUP_ADMIN_DAILY_REMINDER("Follow-up Tickets Admin Reminder", "Send daily digest to all admin users listing all follow-up and site-revisit tickets at 6 AM.");
```

The full enum after the change ends with `FOLLOWUP_ADMIN_DAILY_REMINDER` as the last value (note the semicolon moves there).

- [ ] **Step 2: Seed default settings in `DataInitializer`**

In `DataInitializer.java`, inside the switch in `seedEmailNotificationSettings()`, add new cases before the closing `}` of the switch. The current last case is:

```java
                    case SCHEDULE_REMINDER_DAY_BEFORE:
                    case SCHEDULE_REMINDER_ON_DAY:
                        setting.setEmailEnabled(true);
                        setting.setSmsEnabled(false);
                        break;
                }
```

Change it to:

```java
                    case SCHEDULE_REMINDER_DAY_BEFORE:
                    case SCHEDULE_REMINDER_ON_DAY:
                    case OPEN_TICKETS_DAILY_REMINDER:
                    case FOLLOWUP_ADMIN_DAILY_REMINDER:
                        setting.setEmailEnabled(true);
                        setting.setSmsEnabled(false);
                        break;
                }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/ticketmanager/entity/EmailNotificationAction.java \
        src/main/java/com/example/ticketmanager/config/DataInitializer.java
git commit -m "feat: add OPEN_TICKETS_DAILY_REMINDER and FOLLOWUP_ADMIN_DAILY_REMINDER email notification actions"
```

---

## Task 2: Extend AppProperties + application.yml

**Files:**
- Modify: `src/main/java/com/example/ticketmanager/config/AppProperties.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Add `dailyDigestCron` to the `ScheduleReminder` record**

In `AppProperties.java`, change:

```java
    public record ScheduleReminder(boolean enabled, String dayBeforeCron, String onDayCron) {
    }
```

to:

```java
    public record ScheduleReminder(boolean enabled, String dayBeforeCron, String onDayCron, String dailyDigestCron) {
    }
```

- [ ] **Step 2: Add the property to `application.yml`**

In `application.yml`, under `schedule-reminder:`, add the new line:

```yaml
  schedule-reminder:
    enabled: ${APP_SCHEDULE_REMINDER_ENABLED:true}
    day-before-cron: ${APP_SCHEDULE_REMINDER_DAY_BEFORE_CRON:0 0 18 * * ?}
    on-day-cron: ${APP_SCHEDULE_REMINDER_ON_DAY_CRON:0 0 9 * * ?}
    daily-digest-cron: ${APP_DAILY_DIGEST_CRON:0 0 6 * * ?}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/ticketmanager/config/AppProperties.java \
        src/main/resources/application.yml
git commit -m "feat: add dailyDigestCron config property for 6 AM digest reminders"
```

---

## Task 3: Add repository queries

**Files:**
- Modify: `src/main/java/com/example/ticketmanager/repository/TicketRepository.java`

- [ ] **Step 1: Add `findDistinctAssignedUsersWithActiveTickets` query**

At the end of `TicketRepository` (before the closing `}`), add:

```java
    @Query("select distinct t.assignedTo from Ticket t where t.assignedTo is not null and t.status in :statuses")
    List<AppUser> findDistinctAssignedUsersWithActiveTickets(@Param("statuses") Collection<TicketStatus> statuses);

    @EntityGraph(attributePaths = {"assignedTo", "createdBy"})
    List<Ticket> findByStatusInOrderByUpdatedAtDesc(Collection<TicketStatus> statuses);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/ticketmanager/repository/TicketRepository.java
git commit -m "feat: add repository queries for digest reminder email data"
```

---

## Task 4: Add `TicketDigestRow` + digest send methods to `EmailService`

**Files:**
- Modify: `src/main/java/com/example/ticketmanager/service/EmailService.java`

- [ ] **Step 1: Add `TicketDigestRow` static inner record**

Inside the `EmailService` class body (after the class declaration, before the fields), add:

```java
    public record TicketDigestRow(
            Long id,
            String title,
            String statusLabel,
            String statusColor,
            String priorityLabel,
            String assignedToName,
            String customerName,
            String updatedAt,
            String ticketUrl
    ) {}
```

- [ ] **Step 2: Add `sendOpenTicketsDigest` public method**

Add after `sendScheduleReminderEmail`:

```java
    public void sendOpenTicketsDigest(AppUser user, List<TicketDigestRow> tickets, String generatedDate) {
        Context context = new Context();
        context.setVariable("baseUrl", appProperties.baseUrl());
        context.setVariable("userDisplayName", displayName(user));
        context.setVariable("tickets", tickets);
        context.setVariable("totalCount", tickets.size());
        context.setVariable("generatedDate", generatedDate);
        String subject = "Your Open Tickets — " + generatedDate + " | Ticket Manager";
        String html = templateEngine.process("email/open-tickets-digest", context);
        if (!appProperties.mail().enabled()) {
            log.info("Mail disabled. Open tickets digest skipped. To: {} Tickets: {}", user.getEmail(), tickets.size());
            return;
        }
        sendHtml(user.getEmail(), subject, html);
    }
```

- [ ] **Step 3: Add `sendFollowupAdminDigest` public method**

Add after `sendOpenTicketsDigest`:

```java
    public void sendFollowupAdminDigest(List<String> adminEmails, List<TicketDigestRow> tickets, String generatedDate) {
        Context context = new Context();
        context.setVariable("baseUrl", appProperties.baseUrl());
        context.setVariable("tickets", tickets);
        context.setVariable("totalCount", tickets.size());
        context.setVariable("generatedDate", generatedDate);
        String subject = "Follow-up & Site Revisit Tickets — " + generatedDate + " | Ticket Manager";
        String html = templateEngine.process("email/followup-admin-digest", context);
        if (!appProperties.mail().enabled()) {
            log.info("Mail disabled. Admin follow-up digest skipped. To: {} Tickets: {}", adminEmails, tickets.size());
            return;
        }
        sendHtmlToMultiple(adminEmails, subject, html);
    }
```

- [ ] **Step 4: Add `sendHtmlToMultiple` private helper**

Add alongside `sendHtmlWithCc` (after it):

```java
    private void sendHtmlToMultiple(List<String> toEmails, String subject, String html) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(appProperties.mail().fromAddress(), appProperties.mail().fromName());
            helper.setTo(toEmails.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send multi-recipient email to {}: {}", toEmails, e.getMessage(), e);
        }
    }
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/ticketmanager/service/EmailService.java
git commit -m "feat: add TicketDigestRow record and digest email send methods to EmailService"
```

---

## Task 5: Create `open-tickets-digest.html` template

**Files:**
- Create: `src/main/resources/templates/email/open-tickets-digest.html`

- [ ] **Step 1: Create the template**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<body style="margin:0;padding:0;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#f4f7fb;padding:24px 0;">
  <tr>
    <td align="center">
      <table role="presentation" width="680" cellspacing="0" cellpadding="0"
             style="max-width:680px;background:#ffffff;border-radius:18px;overflow:hidden;box-shadow:0 8px 24px rgba(15,23,42,0.08);">

        <!-- Header -->
        <tr>
          <td style="background:linear-gradient(135deg,#0f766e,#0f6cbd);padding:28px 32px;color:#ffffff;">
            <div style="font-size:12px;letter-spacing:.12em;text-transform:uppercase;opacity:.85;margin-bottom:6px;">Ticket Manager</div>
            <h1 style="margin:0 0 6px;font-size:24px;font-weight:700;">Your Open Tickets</h1>
            <span style="display:inline-block;background:rgba(255,255,255,0.2);color:#fff;font-size:12px;font-weight:700;padding:4px 12px;border-radius:20px;letter-spacing:.05em;"
                  th:text="${generatedDate}">Today</span>
          </td>
        </tr>

        <!-- Body -->
        <tr>
          <td style="padding:28px 32px 8px;">
            <p style="margin:0 0 6px;font-size:16px;">
              Hi <strong th:text="${userDisplayName}">User</strong>,
            </p>
            <p style="margin:0 0 20px;line-height:1.7;font-size:15px;color:#374151;">
              You have <strong th:text="${totalCount}">0</strong> active ticket(s) assigned to you. Here&rsquo;s your daily summary.
            </p>
          </td>
        </tr>

        <!-- Table -->
        <tr>
          <td style="padding:0 32px 24px;">
            <table role="presentation" width="100%" cellspacing="0" cellpadding="0"
                   style="border-collapse:collapse;font-size:13px;">
              <!-- Table header -->
              <tr style="background:#f1f5f9;">
                <th style="text-align:left;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;white-space:nowrap;">#</th>
                <th style="text-align:left;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;">Title</th>
                <th style="text-align:left;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;white-space:nowrap;">Status</th>
                <th style="text-align:left;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;white-space:nowrap;">Priority</th>
                <th style="text-align:left;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;white-space:nowrap;">Last Updated</th>
                <th style="text-align:center;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;white-space:nowrap;">Action</th>
              </tr>
              <!-- Rows -->
              <tr th:each="ticket, iter : ${tickets}"
                  th:style="${iter.odd} ? 'background:#f8fafc;' : 'background:#ffffff;'">
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;color:#64748b;white-space:nowrap;"
                    th:text="'#' + ${ticket.id}">#1</td>
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;max-width:220px;">
                  <span th:text="${ticket.title}" style="display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:220px;">Ticket title</span>
                </td>
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;white-space:nowrap;">
                  <span th:text="${ticket.statusLabel}"
                        th:style="'display:inline-block;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:700;letter-spacing:.04em;color:#fff;background:' + ${ticket.statusColor}"
                        style="display:inline-block;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:700;color:#fff;background:#6b7280;">Open</span>
                </td>
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;color:#374151;white-space:nowrap;"
                    th:text="${ticket.priorityLabel}">Medium</td>
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;color:#64748b;white-space:nowrap;font-size:12px;"
                    th:text="${ticket.updatedAt}">01 Jan 2026</td>
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;text-align:center;white-space:nowrap;">
                  <a th:href="${ticket.ticketUrl}"
                     style="display:inline-block;background:#0f6cbd;color:#ffffff;text-decoration:none;padding:5px 14px;border-radius:6px;font-size:12px;font-weight:600;">View</a>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <!-- Footer note -->
        <tr>
          <td style="padding:0 32px 16px;">
            <p style="margin:0;font-size:13px;color:#6b7280;line-height:1.6;">
              You are receiving this because tickets are assigned to you. This digest is sent daily at 6 AM.
            </p>
          </td>
        </tr>

        <!-- Footer -->
        <tr>
          <td style="border-top:1px solid #e5e7eb;padding:16px 32px;background:#f8fafc;">
            <table role="presentation" width="100%" cellspacing="0" cellpadding="0">
              <tr>
                <td style="font-size:12px;color:#9ca3af;">Ticket Manager &mdash; Daily Digest</td>
                <td align="right" style="font-size:12px;">
                  <a th:href="${baseUrl}" style="color:#0f6cbd;text-decoration:none;">Open Application</a>
                </td>
              </tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>
</table>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/email/open-tickets-digest.html
git commit -m "feat: add open-tickets-digest email template"
```

---

## Task 6: Create `followup-admin-digest.html` template

**Files:**
- Create: `src/main/resources/templates/email/followup-admin-digest.html`

- [ ] **Step 1: Create the template**

```html
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<body style="margin:0;padding:0;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#f4f7fb;padding:24px 0;">
  <tr>
    <td align="center">
      <table role="presentation" width="720" cellspacing="0" cellpadding="0"
             style="max-width:720px;background:#ffffff;border-radius:18px;overflow:hidden;box-shadow:0 8px 24px rgba(15,23,42,0.08);">

        <!-- Header -->
        <tr>
          <td style="background:linear-gradient(135deg,#dc2626,#ea580c);padding:28px 32px;color:#ffffff;">
            <div style="font-size:12px;letter-spacing:.12em;text-transform:uppercase;opacity:.85;margin-bottom:6px;">Ticket Manager &mdash; Admin Digest</div>
            <h1 style="margin:0 0 6px;font-size:24px;font-weight:700;">Follow-up &amp; Site Revisit Tickets</h1>
            <span style="display:inline-block;background:rgba(255,255,255,0.2);color:#fff;font-size:12px;font-weight:700;padding:4px 12px;border-radius:20px;letter-spacing:.05em;"
                  th:text="${generatedDate}">Today</span>
          </td>
        </tr>

        <!-- Body -->
        <tr>
          <td style="padding:28px 32px 8px;">
            <p style="margin:0 0 6px;font-size:16px;">Hi <strong>Admin Team</strong>,</p>
            <p style="margin:0 0 20px;line-height:1.7;font-size:15px;color:#374151;">
              There are <strong th:text="${totalCount}">0</strong> ticket(s) with a <strong>Follow Up</strong> or <strong>Site Revisit</strong> status requiring your attention today.
            </p>
          </td>
        </tr>

        <!-- Table -->
        <tr>
          <td style="padding:0 32px 24px;">
            <table role="presentation" width="100%" cellspacing="0" cellpadding="0"
                   style="border-collapse:collapse;font-size:13px;">
              <!-- Table header -->
              <tr style="background:#f1f5f9;">
                <th style="text-align:left;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;white-space:nowrap;">#</th>
                <th style="text-align:left;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;">Title</th>
                <th style="text-align:left;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;white-space:nowrap;">Status</th>
                <th style="text-align:left;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;white-space:nowrap;">Assigned To</th>
                <th style="text-align:left;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;white-space:nowrap;">Customer</th>
                <th style="text-align:left;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;white-space:nowrap;">Last Updated</th>
                <th style="text-align:center;padding:10px 12px;font-weight:600;color:#475569;border-bottom:2px solid #e2e8f0;white-space:nowrap;">Action</th>
              </tr>
              <!-- Rows -->
              <tr th:each="ticket, iter : ${tickets}"
                  th:style="${iter.odd} ? 'background:#f8fafc;' : 'background:#ffffff;'">
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;color:#64748b;white-space:nowrap;"
                    th:text="'#' + ${ticket.id}">#1</td>
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;max-width:180px;">
                  <span th:text="${ticket.title}" style="display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:180px;">Ticket title</span>
                </td>
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;white-space:nowrap;">
                  <span th:text="${ticket.statusLabel}"
                        th:style="'display:inline-block;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:700;letter-spacing:.04em;color:#fff;background:' + ${ticket.statusColor}"
                        style="display:inline-block;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:700;color:#fff;background:#ea580c;">Follow Up</span>
                </td>
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;color:#374151;white-space:nowrap;"
                    th:text="${ticket.assignedToName}">Unassigned</td>
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;color:#374151;white-space:nowrap;max-width:130px;">
                  <span th:text="${ticket.customerName}" style="display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:130px;">&mdash;</span>
                </td>
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;color:#64748b;white-space:nowrap;font-size:12px;"
                    th:text="${ticket.updatedAt}">01 Jan 2026</td>
                <td style="padding:10px 12px;border-bottom:1px solid #f1f5f9;text-align:center;white-space:nowrap;">
                  <a th:href="${ticket.ticketUrl}"
                     style="display:inline-block;background:#0f6cbd;color:#ffffff;text-decoration:none;padding:5px 14px;border-radius:6px;font-size:12px;font-weight:600;">View</a>
                </td>
              </tr>
            </table>
          </td>
        </tr>

        <!-- Footer note -->
        <tr>
          <td style="padding:0 32px 16px;">
            <p style="margin:0;font-size:13px;color:#6b7280;line-height:1.6;">
              This digest is sent to all admin users daily at 6 AM and includes all organisation-level follow-up and site revisit tickets.
            </p>
          </td>
        </tr>

        <!-- Footer -->
        <tr>
          <td style="border-top:1px solid #e5e7eb;padding:16px 32px;background:#f8fafc;">
            <table role="presentation" width="100%" cellspacing="0" cellpadding="0">
              <tr>
                <td style="font-size:12px;color:#9ca3af;">Ticket Manager &mdash; Admin Follow-up Digest</td>
                <td align="right" style="font-size:12px;">
                  <a th:href="${baseUrl}" style="color:#0f6cbd;text-decoration:none;">Open Application</a>
                </td>
              </tr>
            </table>
          </td>
        </tr>

      </table>
    </td>
  </tr>
</table>
</body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/templates/email/followup-admin-digest.html
git commit -m "feat: add followup-admin-digest email template"
```

---

## Task 7: Add digest methods to `ScheduleReminderService` + tests

**Files:**
- Modify: `src/main/java/com/example/ticketmanager/service/ScheduleReminderService.java`
- Create: `src/test/java/com/example/ticketmanager/service/ScheduleReminderDigestTest.java`

- [ ] **Step 1: Write the failing tests first**

Create `src/test/java/com/example/ticketmanager/service/ScheduleReminderDigestTest.java`:

```java
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

import java.time.LocalDateTime;
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

    // ---- sendDailyUserOpenTicketsDigest ----

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

        Ticket t1 = makeTicket(101L, "Fix router", TicketStatus.OPEN, TicketPriority.HIGH, user1);
        Ticket t2 = makeTicket(102L, "Check cables", TicketStatus.ON_HOLD, TicketPriority.MEDIUM, user2);
        when(ticketRepository.findByAssignedToIdAndStatusInOrderByUpdatedAtDesc(eq(1L), anyList()))
                .thenReturn(List.of(t1));
        when(ticketRepository.findByAssignedToIdAndStatusInOrderByUpdatedAtDesc(eq(2L), anyList()))
                .thenReturn(List.of(t2));

        service.sendDailyUserOpenTicketsDigest();

        verify(emailService, times(1)).sendOpenTicketsDigest(eq(user1), anyList(), anyString());
        verify(emailService, times(1)).sendOpenTicketsDigest(eq(user2), anyList(), anyString());
    }

    @Test
    void userDigest_rowsMappedCorrectly() {
        when(notificationSettingsService.isEmailEnabled(EmailNotificationAction.OPEN_TICKETS_DAILY_REMINDER))
                .thenReturn(true);

        AppUser user = makeUser(1L, "alice@example.com", "Alice", "Smith");
        when(ticketRepository.findDistinctAssignedUsersWithActiveTickets(anyList()))
                .thenReturn(List.of(user));

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

    // ---- sendDailyAdminFollowupDigest ----

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
        Ticket t = makeTicket(200L, "Follow up client", TicketStatus.FOLLOW_UP, TicketPriority.MEDIUM, assignee);
        when(ticketRepository.findByStatusInOrderByUpdatedAtDesc(anyList())).thenReturn(List.of(t));
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

    // ---- helpers ----

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
        t.setUpdatedAt(LocalDateTime.now());
        return t;
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail (methods don't exist yet)**

```bash
./mvnw test -pl . -Dtest=ScheduleReminderDigestTest -q 2>&1 | tail -20
```

Expected: compilation error — `sendDailyUserOpenTicketsDigest` and `sendDailyAdminFollowupDigest` not found, `setUpdatedAt` may not exist.

If `Ticket` doesn't have `setUpdatedAt`, use reflection or leave the `updatedAt` field null in tests (the row builder must handle null).

- [ ] **Step 3: Add imports and new constants to `ScheduleReminderService`**

Add to the import block:

```java
import com.example.ticketmanager.entity.AppUser;
import java.time.format.DateTimeFormatter;
```

Add new constants after `EXCLUDED_STATUSES`:

```java
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
```

- [ ] **Step 4: Add `toDigestRow` private helper**

Add at the bottom of `ScheduleReminderService`, before the closing `}`:

```java
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
        String ticketUrl = appProperties.baseUrl() + "/tickets/" + ticket.getId();
        return new EmailService.TicketDigestRow(
                ticket.getId(), ticket.getTitle(), statusLabel, statusColor,
                priorityLabel, assignedToName, customerName, updatedAt, ticketUrl
        );
    }
```

- [ ] **Step 5: Add `sendDailyUserOpenTicketsDigest` scheduled method**

Add after `sendOnDayReminders()` (before the last `}`):

```java
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
```

- [ ] **Step 6: Add `sendDailyAdminFollowupDigest` scheduled method**

Add after `sendDailyUserOpenTicketsDigest()`:

```java
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
```

- [ ] **Step 7: Run tests — all should pass**

```bash
./mvnw test -pl . -Dtest=ScheduleReminderDigestTest -q 2>&1 | tail -20
```

Expected output:
```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```

- [ ] **Step 8: Run full test suite to check for regressions**

```bash
./mvnw test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/example/ticketmanager/service/ScheduleReminderService.java \
        src/test/java/com/example/ticketmanager/service/ScheduleReminderDigestTest.java
git commit -m "feat: add daily user open-tickets digest and admin follow-up digest scheduled emails"
```
