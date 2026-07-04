# TicketManager — Project Requirements, Features & Functionality

Consolidated reference for the TicketManager application: what it does, how it's built, and where things live. This file supersedes several scattered root-level docs (see "Changelog / Known Fixes" at the end) and is meant to be the single starting point for both human contributors and Claude in future sessions.

## 1. Overview

TicketManager is a Spring Boot service-ticket management system for a field-service/installation-and-repair business. It supports internal staff (admins, managers, agents/technicians), external vendors, and standard end-users, covering the full ticket lifecycle from lead capture through site visits, resolution, billing, and payment tracking — plus real-time chat, notifications, scheduled reminders/digests, and Excel reporting.

**Stack**: Spring Boot 3.4.3 (Java), PostgreSQL, Flyway migrations, Thymeleaf server-rendered UI, Spring Security (stateless JWT), WebSocket/STOMP (real-time chat), JavaMailSender (email), Apache POI (Excel reports), Caffeine (caching). Base package: `com.example.ticketmanager`.

## 2. User Roles & Permissions

Roles are seeded in `config/DataInitializer.java`:

| Role | Purpose |
|---|---|
| `ROLE_ADMIN` | Full access: ticket management + entire admin panel (users, roles, role-features, email notifications, staff billing, reports). |
| `ROLE_MANAGER` | Ticket operations management (manage/review/all-view tickets, chat, dashboard) — no admin panel. |
| `ROLE_AGENT` | Staff/technician: assigned-ticket dashboard, view tickets, site-visit logging, chat. |
| `ROLE_VENDOR` | External vendor: create/manage vendor-owned tickets, "My Tickets" view, own dashboard. |
| `ROLE_USER` | Standard end-user: dashboard, profile, view tickets, chat. |

Permissions are fine-grained via an `AppFeature` enum (24 values, e.g. `TICKETS_MANAGE`, `ADMIN_STAFF_BILLING`, `DASHBOARD_ALL_TICKET_STATUS`) attached to each `Role` via `role_features`. Each feature maps to a Spring Security authority `FEATURE_<name>`, giving per-role, per-capability access control independent of the coarse `ROLE_*` grouping — this is how the admin UI lets you customize what a role can see/do without code changes.

**Auth**: Stateless JWT (not session-based). `JwtAuthenticationFilter` reads/validates a `TM_TOKEN` cookie and builds an `AppUserPrincipal` carrying all `ROLE_*`/`FEATURE_*` authorities — no DB hit per request. Passwords are BCrypt-hashed. Separate login/register flows exist for standard users (`/login`, `/register`) and vendors (`/vendor/login`, `/vendor/register`), plus password reset and email/mobile (OTP) verification flows. Vendor and staff views reuse the same ticket templates filtered by role/feature flags — there's no separate "portal" template set beyond login/register.

## 3. Core Domain Model

**Ticket** (`Ticket`) is the central entity:
- Identity/description: title, description, address, `serviceType`, `leadFrom`, `locationLink`.
- Workflow: `status` (11-state enum, see below), `priority`, `scheduleDate`, `siteVisits` counter, self-referential `parentTicket` (chains follow-up/site-revisit tickets).
- Ownership: `createdBy`, `updatedBy`, `assignedTo`, `vendorUser` (for vendor-owned tickets), ManyToMany `serviceUsers` (multiple assigned staff).
- Customer snapshot: name/email/phone/address fields captured at creation time, plus a soft link `customerAddressReferenceId` to the reusable `CustomerAddress` book.
- Commercial: `pricingModel` (FIXED_PRICE / HOURLY_RATE), `estimatedCost`/`actualCost`, `billingStatus` (UNPAID/PAID), `billingPaidAt`.
- Children: `attachments` (file uploads), `comments` (threaded), `siteVisitHistory` (geo-tagged visits), `payments` (multi-leg payment tracking).

**Key enums**:
- `TicketStatus`: LEADS, OPEN, SITE_VISITED, IN_PROGRESS, ON_HOLD, FOLLOW_UP, SITE_REVISIT, QUOTED, RESOLVED, CLOSED, CANCELLED.
- `TicketPriority`: LOW, MEDIUM, HIGH, CRITICAL.
- `TicketServiceType`: LEADS, INSTALLATION, SERVICE, AMC, SITE_VISIT, REPAIR, MAINTENANCE.
- `TicketPaymentType`: CLIENT, TECHNICIAN, VENDOR. `TicketPaymentMode`: CASH, BANK_TRANSFER, UPI.

**Other entities**: `AppUser` (users, with address/business fields for vendors, profile image as BYTEA), `UserIdProof` (ID document + admin verification workflow), `CustomerAddress` (shared address book), `TicketPayment` (per-ticket payment legs), `TicketAttachment`/`TicketComment`/`TicketSiteVisit`, `Notification`, `ChatConversation`/`ChatMessage`, `EmailNotificationSetting` (per-action email/SMS toggle), plus standard token entities for email verification, password reset, and mobile OTP verification.

## 4. Feature Catalog

### Ticket Management
Full CRUD with multipart file attachments, JPA `Specification`-based filtering/search (by scope, assignment, vendor, status, priority), parent-ticket linking for follow-ups/site-revisits, threaded comments, geo-tagged site-visit logging, and dashboard metrics. Visibility is scope-aware: vendors only see their own tickets, agents see assigned tickets, managers/admins see everything.

### Customer Address Book
Reusable customer records (`CustomerAddress`) looked up by name/email/phone during ticket creation, with auto-fill (single match) or dropdown selection (multiple matches). Vendors only see addresses they created; admins see all. Endpoints under `/api/customers`.

### Billing & Payments
Two layers: a simple ticket-level `billingStatus` (UNPAID/PAID) for overall billing state, and a detailed `TicketPayment` model tracking multiple payment legs per ticket by type (CLIENT/TECHNICIAN/VENDOR) with expected vs. actual price, payment mode, and datetime. `StaffBillingService` aggregates this into per-staff billing summaries, detail pages, and printable invoices under the admin panel.

### ID Proof Verification
Users upload ID proof documents (stored as BYTEA in Postgres); admins review and mark them `verified`/not with notes, via `/api/admin/users/{userId}/verify-id-proofs`. UI-gated to Vendor/Agent roles.

### Notifications, Email & SMS
`Notification` entity backs an in-app bell/unread-count feed. `EmailService` sends verification, password-reset, ticket-notification, schedule-reminder, and digest emails (with attachments where relevant); `SmsService` handles OTP and ticket-activity SMS alerts. Every notification type maps to an `EmailNotificationAction` enum value that admins can individually enable/disable per channel (email/SMS) via the admin panel — no code changes needed to mute a notification type.

### Real-Time Chat
WebSocket/STOMP-based (`/ws` endpoint, SockJS fallback) direct messaging between users, with typing indicators and read receipts (`deliveredAt`/`readAt` on `ChatMessage`). Optionally linked to a ticket via `relatedTicket`.

### Scheduled Reminders & Digests
Four daily cron jobs in `ScheduleReminderService` (times overridable via `application.yml`):
- 6 PM — next-day schedule reminders.
- 9 AM — same-day schedule reminders.
- 6 AM — per-user digest of open/active tickets.
- 6 AM — admin-wide digest of FOLLOW_UP/SITE_REVISIT tickets.

Plus `ReportSchedulerService.generateDailyReports()` on a configurable cron (`app.reports.schedule.cron`).

### Excel Reporting
`ReportService` generates downloadable/emailable Excel reports (Apache POI) by type and date range, with a "recent reports" history for re-download.

### Admin Panel
User management, role CRUD, role→feature assignment, email-notification settings, ID-proof review, staff billing, and reports — all under `/admin/**` and `/api/admin/**`.

## 5. API Surface (by controller)

| Controller | Base | Responsibility |
|---|---|---|
| `ViewController` | `/` | Thymeleaf page routing for every screen (dashboard, tickets, admin pages, chat, auth pages). |
| `AuthRestController` | `/api/auth` | Register/login (standard + vendor), logout, email verification, password reset, `/me`, mobile OTP. |
| `TicketRestController` | `/api/tickets` | Ticket CRUD, attachments, comments, site visits, search, `/metrics`. |
| `AdminRestController` | `/api/admin` | Users, roles, features, email-notification settings, ID-proof review, staff billing, reports. |
| `CustomerRestController` | `/api/customers` | Customer/address lookup and address-book CRUD. |
| `DashboardRestController` | `/api/dashboard` | Chart data (ticket status, user counts by role, metrics). |
| `NotificationRestController` | `/api/notifications` | List + unread count. |
| `ChatRestController` / `ChatWebSocketController` | `/api/chat`, `/ws` | Conversation history + real-time STOMP messaging. |
| `UserRestController` | `/api/users` | Profile, password change, profile picture, ID-proof upload/view. |

## 6. Frontend

Flat Thymeleaf template set under `src/main/resources/templates/`:
- **Auth**: `login.html`, `register.html`, `vendor-login.html`, `vendor-register.html`, `reset-password.html`, `verify-email.html`.
- **Core**: `dashboard.html`, `profile.html`, `chat.html`.
- **Tickets**: `tickets.html` (list, reused across pending/resolved/created/review/all views), `ticket-create.html`, `ticket-edit.html`, `ticket-view.html`.
- **Admin**: `admin-support-tickets.html`, `admin-users.html`, `admin-user-details.html`, `admin-roles.html`, `admin-role-features.html`, `admin-email-notifications.html`, `admin-staff-billing.html`, `admin-staff-billing-details.html`, `admin-staff-billing-invoice.html`, `admin-reports.html`.
- **Email templates**: `email/account-verification.html`, `email/password-reset.html`, `email/ticket-notification.html`, `email/schedule-reminder.html`, `email/open-tickets-digest.html`, `email/followup-admin-digest.html`.
- **Errors**: `error/403.html`, `error/404.html`, `error/500.html`, `error/error.html`.
- **Shared**: `fragments/layout.html` (nav/layout, gated per role via `GlobalViewModelAdvice`).

## 7. File Storage

Two storage strategies are in use:
- **Ticket attachments** — filesystem, under configurable `app.upload-dir` (default `uploads`, override via `APP_UPLOAD_DIR`).
- **Profile images & ID-proof documents** — BYTEA blobs directly in Postgres.
- Multipart limits: 10MB per file, 25MB per request.

## 8. Database Migrations

Flyway migrations in `src/main/resources/db/migration/`, currently V1–V13 (14 files — two files share the `V6` version prefix: `V6__add_cancelled_ticket_status.sql` and `V6__update_ticket_service_types.sql`; worth renumbering one if it ever causes a Flyway conflict). Notable evolution:
- V5 added ID-proof fields directly to `users`; superseded by the dedicated `user_id_proofs` table in V8.
- V7/V9 cleaned up legacy `ticket_service_types` string values (e.g. MAINTENANCE/REPAIR variants) into canonical enum values.
- V10 added the QUOTED status; V12 added further statuses and converted `schedule_date` from DATE to TIMESTAMP.
- V13 added the `ticket_payments` table.

**Known pitfall**: `@Enumerated(STRING)` columns must use `columnDefinition="varchar(N)"` — otherwise Hibernate's auto-generated check constraint goes stale whenever a new enum value is added, which can break production deploys. Apply this to every ticket-status-like enum column going forward.

## 9. Deployment

See `DEPLOYMENT.md` for the full ops runbook (build, Docker Compose, Nginx/SSL, rollback). See `HELP.md` for Spring Boot reference links. Key points worth knowing up front:
- Docker Compose brings up the app + Postgres + Nginx (reverse proxy, ACME/SSL support for cert renewal).
- Production profile is active via Docker Compose; double-check `ddl-auto` is set to `validate` (not `update`) in the prod profile before relying on Flyway as the source of truth for schema changes.
- No automated test suite currently covers payment/billing features — manual verification is required after changes in that area.

## 10. Changelog / Known Fixes (condensed)

- **Vendor "My Tickets" NULL-handling fix** — `TicketService`'s JPA `Specification` methods (`vendorVisibleSpecification`, `assignedToSpecification`, `vendorSpecification`, `scopeForUser`) previously used `cb.equal()` directly against nullable `assignedTo`/`vendorUser` foreign keys, which silently fails to match when the value is NULL (e.g. newly created/unassigned tickets). Fixed by adding `isNotNull()` guards before the equality check. Deployed and verified.
- **ID-proof UI redesign** — Admin user-details modal and user profile page's ID-proof section was redesigned with a glass-card visual style, restricted to Vendor/Agent roles only, and the status copy was corrected from a misleading "verified" label to "Upload successful & pending for admin approval" until an admin actually verifies it.
- **Ticket comment cache bug** — comment mutations (add/update/delete) previously didn't evict the `ticketComments` cache (10-minute TTL), so new comments could take up to 10 minutes to appear. Fixed by adding `@CacheEvict` to `TicketService.addComment`/`updateComment`/`deleteComment`.
- **Login redirect** — both standard and vendor login now redirect to `/dashboard` after success (previously `/tickets`).

---
*This file replaces `CODE_CHANGES_DETAILED.md`, `CUSTOMER_ADDRESS_FEATURE.md`, `DEPLOYMENT_COMPLETE.md`, `VENDOR_TICKETS_FIX.md`, `QUICK_REFERENCE.md`, and `UI_IMPROVEMENTS_CHANGELOG.md`, which documented overlapping subsets of the above and have been removed. `DEPLOYMENT.md` and `HELP.md` remain as separate ops/build references.*
