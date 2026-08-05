# Ticket Digest Reminder Emails — Design Spec

**Date:** 2026-06-06  
**Status:** Approved  
**Branch:** develop_30062026

---

## Overview

Add two new daily digest email reminders that run at 6 AM:

1. **User Open Tickets Digest** — each assigned user receives a table of all their active (non-terminal) tickets.
2. **Admin Follow-up Digest** — each admin user receives a table of all FOLLOW_UP and SITE_REVISIT tickets.

Both are toggleable via the existing Email Notification Settings page.

---

## Email Notification Actions

Two new values added to `EmailNotificationAction` enum:

| Enum Value | Label | Description |
|---|---|---|
| `OPEN_TICKETS_DAILY_REMINDER` | Open Tickets Daily Reminder | Send daily digest to each user listing their active open/in-progress/on-hold tickets at 6 AM. |
| `FOLLOWUP_ADMIN_DAILY_REMINDER` | Follow-up Tickets Admin Reminder | Send daily digest to admin users listing all follow-up and site-revisit tickets at 6 AM. |

Default seeded state: email enabled, SMS disabled (same as other reminder types).

---

## Configuration

### AppProperties

Add `dailyDigestCron` field to the existing `ScheduleReminder` record:

```java
public record ScheduleReminder(boolean enabled, String dayBeforeCron, String onDayCron, String dailyDigestCron) {}
```

### application.yml

```yaml
app:
  schedule-reminder:
    daily-digest-cron: ${APP_DAILY_DIGEST_CRON:0 0 6 * * ?}
```

---

## Repository Queries

Two new queries in `TicketRepository`:

### Query 1 — Distinct assigned users with active tickets

```java
@Query("select distinct t.assignedTo from Ticket t where t.assignedTo is not null and t.status in :statuses")
@EntityGraph(...)
List<AppUser> findDistinctAssignedUsersWithActiveTickets(@Param("statuses") Collection<TicketStatus> statuses);
```

### Query 2 — All tickets by status list (for admin digest)

```java
@EntityGraph(attributePaths = {"assignedTo", "createdBy"})
List<Ticket> findByStatusInOrderByUpdatedAtDesc(Collection<TicketStatus> statuses);
```

Note: `findByAssignedToIdAndStatusInOrderByUpdatedAtDesc` already exists for per-user ticket fetching.

---

## Email Templates

Two new Thymeleaf templates under `src/main/resources/templates/email/`, matching the existing theme:

- **Header**: `linear-gradient(135deg,#0f766e,#0f6cbd)` with white text
- **Background**: `#f4f7fb`, white card, `border-radius:18px`, `box-shadow`
- **Table**: bordered, header row with light background, alternating rows, status as inline color badge
- **CTA per row**: "View" link opening directly to that ticket

### `open-tickets-digest.html` — Template variables

| Variable | Type | Description |
|---|---|---|
| `userDisplayName` | String | Recipient's display name |
| `tickets` | List\<TicketDigestRow\> | Active tickets assigned to this user |
| `totalCount` | int | Total ticket count |
| `generatedDate` | String | Formatted today's date |
| `baseUrl` | String | App base URL (for footer link) |

`TicketDigestRow` fields: `id`, `title`, `status`, `statusLabel`, `statusColor`, `priority`, `updatedAt`, `ticketUrl`

Table columns: **#**, **Title**, **Status**, **Priority**, **Last Updated**, **Action**

### `followup-admin-digest.html` — Template variables

| Variable | Type | Description |
|---|---|---|
| `tickets` | List\<TicketDigestRow\> | All FOLLOW_UP + SITE_REVISIT tickets (org-wide) |
| `totalCount` | int | Total ticket count |
| `generatedDate` | String | Formatted today's date |
| `baseUrl` | String | App base URL |

`TicketDigestRow` additional fields: `assignedToName` (may be "Unassigned"), `customerName`

Table columns: **#**, **Title**, **Status**, **Assigned To**, **Customer**, **Last Updated**, **Action**

> Both templates use a shared `TicketDigestRow` record defined as a static inner record of `EmailService`. No new entity or DTO file needed.

---

## EmailService Methods

```java
public void sendOpenTicketsDigest(AppUser user, List<Ticket> tickets, String formattedDate) { ... }

public void sendFollowupAdminDigest(List<String> adminEmails, List<Ticket> tickets, String formattedDate) { ... }
```

`sendOpenTicketsDigest`:
1. Build Thymeleaf context with user variables
2. Process `open-tickets-digest.html`
3. Guard on `appProperties.mail().enabled()`
4. Call existing `sendHtml(to, subject, html)`

`sendFollowupAdminDigest`:
1. Build Thymeleaf context (no per-admin name — addressed to "Admin Team")
2. Process `followup-admin-digest.html`
3. Guard on `appProperties.mail().enabled()`
4. Send **one email** with all admin email addresses in the TO field using `sendHtmlToMultiple(adminEmails, subject, html)` — a new helper that sets multiple TO recipients on a single `MimeMessage`

---

## ScheduleReminderService Changes

### Active statuses constant

```java
private static final List<TicketStatus> ACTIVE_STATUSES = List.of(
    TicketStatus.LEADS, TicketStatus.OPEN, TicketStatus.SITE_VISITED,
    TicketStatus.IN_PROGRESS, TicketStatus.ON_HOLD, TicketStatus.FOLLOW_UP,
    TicketStatus.SITE_REVISIT, TicketStatus.QUOTED
);
```

### New method 1 — User digest (6 AM)

```java
@Scheduled(cron = "${app.schedule-reminder.daily-digest-cron:0 0 6 * * ?}")
public void sendDailyUserOpenTicketsDigest() { ... }
```

Logic:
1. Guard: `scheduleReminder.enabled()` + `OPEN_TICKETS_DAILY_REMINDER` notification setting
2. Find distinct assigned users with active tickets
3. For each user: fetch their active tickets → call `emailService.sendOpenTicketsDigest(user, tickets, date)`
4. Log counts per user + errors per ticket

### New method 2 — Admin digest (6 AM)

```java
@Scheduled(cron = "${app.schedule-reminder.daily-digest-cron:0 0 6 * * ?}")
public void sendDailyAdminFollowupDigest() { ... }
```

Logic:
1. Guard: `scheduleReminder.enabled()` + `FOLLOWUP_ADMIN_DAILY_REMINDER` notification setting
2. Fetch all FOLLOW_UP + SITE_REVISIT tickets (org-wide, regardless of creator or assignee)
3. Skip entirely if no tickets (no empty digest emails)
4. Get all admin email addresses via `userService.getAdminEmails()`
5. Send **one email** to all admins in a single TO call: `emailService.sendFollowupAdminDigest(adminEmails, tickets, date)`
6. Log ticket count + admin email count + errors

---

## Status Badge Colors

| Status | Color |
|---|---|
| OPEN | `#16a34a` (green) |
| LEADS | `#6d28d9` (purple) |
| SITE_VISITED | `#0ea5e9` (sky blue) |
| IN_PROGRESS | `#0f6cbd` (blue) |
| ON_HOLD | `#d97706` (amber) |
| FOLLOW_UP | `#ea580c` (orange) |
| SITE_REVISIT | `#dc2626` (red) |
| QUOTED | `#059669` (emerald) |

---

## DataInitializer

Add cases for the two new actions in `seedEmailNotificationSettings()`:

```java
case OPEN_TICKETS_DAILY_REMINDER:
case FOLLOWUP_ADMIN_DAILY_REMINDER:
    setting.setEmailEnabled(true);
    setting.setSmsEnabled(false);
    break;
```

---

## Out of Scope

- SMS for digest reminders (toggled off by default, no SMS implementation needed)
- Vendor-specific digest (vendors are not assigned via `assignedTo` in this flow)
- Pagination of large ticket lists in email (digest sent as-is; no cap enforced in v1)
