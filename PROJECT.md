# TicketManager

## 1. Overview

TicketManager is a service-ticket management platform for a field-service/installation business (internet/CCTV/network installation, AMC, repairs, site visits). It supports internal staff (admins, managers, technicians/agents), external vendors who create and track their own tickets, and standard end users, with ticket lifecycle tracking, billing/payment capture, real-time chat, and email/SMS notifications.

**Tech stack**
- **Language / runtime**: Java 21
- **Framework**: Spring Boot 3.4.3 (`spring-boot-starter-parent`)
- **Web**: Spring MVC (Thymeleaf server-rendered views) + Spring REST controllers under `/api/**`
- **Security**: Spring Security 6, stateless, custom JWT filter (`jjwt` 0.12.6), cookie (`TM_TOKEN`, httpOnly) or `Authorization: Bearer` header
- **Persistence**: Spring Data JPA/Hibernate, PostgreSQL (`postgres:16-alpine` in Docker), Flyway-style versioned SQL migrations in `src/main/resources/db/migration` (`spring.sql.init.mode: never` — schema is migration-driven, not Hibernate `ddl-auto`)
- **Real-time**: Spring WebSocket + STOMP (`spring-boot-starter-websocket`, `spring-security-messaging`) for chat, notifications, and list-refresh events
- **Caching**: Spring Cache + Caffeine (`usersByEmail`, `tickets`, `customerAddresses`, `ticketComments` cache regions; 10 min TTL, 5000 entries)
- **Email**: `spring-boot-starter-mail`, Thymeleaf HTML email templates
- **Reporting**: Apache POI (`poi`, `poi-ooxml` 5.2.5) — Excel (.xlsx) report generation
- **Build**: Maven (`mvnw`), Lombok for boilerplate reduction
- **Templating**: Thymeleaf + `thymeleaf-extras-springsecurity6`

**High-level architecture**

The app is a single Spring Boot deployable combining:
1. **Server-rendered MVC layer** (`controller/ViewController.java`, `controller/StaticResourceController.java`) — returns Thymeleaf view names for pages (login, dashboard, tickets, admin, chat, etc.). Views fetch their data client-side via the REST API layer using JS/fetch.
2. **REST API layer** (`controller/api/*`) — JSON endpoints under `/api/**` consumed by the Thymeleaf pages' JavaScript. Secured with method-level `@PreAuthorize` using a feature-authority model (see §3).
3. **WebSocket layer** (`controller/api/ChatWebSocketController.java` + `config/WebSocketConfig.java`) — STOMP over SockJS at `/ws`, used for chat messaging/typing indicators, ticket comment/list refresh events, and per-user notification pushes.
4. **Service layer** (`service/*`, `service/impl` pattern is not used — services are concrete classes directly implementing business logic) — encapsulates all business rules; controllers are thin.
5. **Scheduled jobs** (`ScheduleReminderService`, `ReportSchedulerService`) — cron-driven reminder emails and daily report generation.

Authentication is fully stateless: on login the server issues a JWT (in an httpOnly cookie) that embeds the user's full authority set (`ROLE_*` + `FEATURE_*`), so most requests need no DB round-trip to authorize (`JwtAuthenticationFilter`, `AppUserPrincipal`).

---

## 2. Domain Model

26 JPA entities in `entity/`, grouped by area:

### Ticketing
| Entity | Purpose |
|---|---|
| `Ticket` | Core work-order record: title/description, service type, status, priority, customer + address snapshot, assignment (technician, vendor, service users), pricing/cost, billing status, attachments, comments, site visits, payments, optional parent ticket (sub-ticket linking) |
| `TicketAttachment` | File uploaded to a ticket (stored on disk under `app.upload-dir`, metadata in DB) |
| `TicketComment` | Threaded comment/reply on a ticket (self-referential `parent`/`replies`) |
| `TicketSiteVisit` | Geo-tagged site visit log entry (lat/long, notes) recorded by an agent |
| `TicketPayment` | Payment line for a ticket, one row per `TicketPaymentType` (CLIENT / TECHNICIAN / VENDOR) — expected/actual price, mode, datetime, status |
| `CustomerAddress` | Reusable customer address record (decoupled from tickets so the same customer/address can be reused across tickets) |

Ticketing enums: `TicketStatus`, `TicketPriority`, `TicketServiceType`, `TicketBillingStatus`, `TicketPricingModel`, `TicketPaymentType`, `TicketPaymentMode`.

**Key relationships**: `Ticket.createdBy` / `updatedBy` / `assignedTo` → `AppUser`; `Ticket.vendorUser` → `AppUser` (vendor owner); `Ticket.serviceUsers` → `AppUser` (many-to-many, additional staff on the job); `Ticket.parentTicket` → `Ticket` (self-referential, for sub-tickets); `Ticket.payments` → `TicketPayment` (one-to-many, cascade all); `Ticket.customerAddressReferenceId` is a soft reference to `CustomerAddress`.

### Users, Roles & Access
| Entity | Purpose |
|---|---|
| `AppUser` | Application user/account — profile, address, company info (for vendors), profile image (BYTEA), email/phone verification flags, roles |
| `Role` | Named role (`ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`, `ROLE_VENDOR`, `ROLE_USER`) holding a set of `AppFeature`s |
| `AppFeature` | Enum of fine-grained permissions (e.g. `TICKETS_MANAGE`, `ADMIN_STAFF_BILLING`); exposed to Spring Security as authority string `FEATURE_<name>` |
| `UserIdProof` | KYC document (Aadhar/PAN/etc.) uploaded by a user, with admin verification workflow |
| `EmailVerificationToken` | One-time token for email verification (24h expiry) |
| `MobileVerificationToken` | One-time OTP for phone verification (10 min expiry) |
| `PasswordResetToken` | One-time token for forgot-password flow (2h expiry) |

`AppUser.roles` is many-to-many to `Role`; `Role.features` is an `@ElementCollection` of `AppFeature` enums (table `role_features`).

### Billing
Billing is modeled on `Ticket` itself (`billingStatus`, `billingPaidAt`, `estimatedCost`, `actualCost`) plus the `TicketPayment` child entity for per-party (client/technician/vendor) payment tracking. There is no separate `Invoice` entity — invoices are generated on the fly (`admin-staff-billing-invoice.html`) from ticket + payment data.

### Chat & Notifications
| Entity | Purpose |
|---|---|
| `ChatConversation` | A 1:1 (or potentially multi-party) chat thread — `participants` many-to-many to `AppUser` |
| `ChatMessage` | A message within a conversation, optionally linked to a `Ticket`, with delivered/read timestamps |
| `Notification` | In-app notification row per user (`NotificationType`, read flag, reference to source entity) |
| `NotificationType` | `TICKET_UPDATED`, `COMMENT_ADDED`, `CHAT_MESSAGE`, `ACCOUNT_EVENT` |
| `EmailNotificationAction` | Enum of every notifiable system action (16 values — account verification, ticket lifecycle, reminders, digests, admin/vendor activity, etc.) |
| `EmailNotificationSetting` | Per-`EmailNotificationAction` admin-configurable toggle for email/SMS delivery (keyed by the enum itself) |

---

## 3. Roles & Access Control

**Roles** (seeded in `config/DataInitializer.java`, `seedEssentialData()`): `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`, `ROLE_VENDOR`, `ROLE_USER` — five roles total.

There is **no explicit numeric role-hierarchy field** in the code (no `level`/`rank` column on `Role`). Access control is entirely **feature-based**: each `Role` holds a `Set<AppFeature>`, and Spring Security authorities are the union of the role name (`ROLE_X`) plus `FEATURE_<feature>` for every feature on every active role the user has (`AppUserPrincipal`). Controllers/services use `@PreAuthorize("hasAuthority('FEATURE_...')")` or `hasRole(...)`, not a hierarchy check. Admins are simply the role with the largest feature set — treat any "5-level hierarchy" as an informal description of role count, not an enforced mechanism.

Default seeded feature grants per role:
- **ROLE_ADMIN**: everything — dashboard (all charts), profile, full ticket access (view/manage/create/review/all-view), chat, and all `ADMIN_*` features (support tickets, user management, role management, role-feature assignment, email notification config, staff billing, reports).
- **ROLE_MANAGER**: dashboard (mine + all-ticket charts), profile, ticket view/manage/create-standard/review/all-view, chat. No admin-panel access.
- **ROLE_AGENT** (technician): dashboard (mine only), profile, ticket view, site-visit editing, chat. Cannot manage/create tickets or view all tickets; cannot see ticket pricing (masked in API responses); cannot close tickets.
- **ROLE_VENDOR**: dashboard (mine), profile, ticket view/manage, vendor-specific ticket creation, "my tickets" view (tickets they created). Vendor-created tickets restrict visibility of assignee/service-user identities from the vendor's own view.
- **ROLE_USER**: dashboard, profile, ticket view, chat. Minimal — mainly a customer/requester-facing role.

Admin-only endpoints additionally require the literal `ROLE_ADMIN` authority in combination with a feature flag for the most sensitive actions (e.g. staff billing status updates: `hasAuthority('FEATURE_ADMIN_STAFF_BILLING') and hasAuthority('ROLE_ADMIN')`). Roles and their feature grants are themselves editable at runtime via the Admin > Roles / Role Features UI (`AdminRestController`), so the seeded defaults above are the *initial* state, not a hard-coded constraint.

---

## 4. Features & Functionality

### Ticket Management
Implemented by `TicketRestController` + `TicketService` (1231 lines — the largest service).
- **Create/update/delete tickets** with multipart file attachments, customer details (either freeform or linked to a reusable `CustomerAddress`), scheduling, priority, service type, pricing model, and per-party payment info.
- **Scoped listing**: admin/all-view scope vs. "mine" scope (assigned-to-me, created-by-me for vendors, assigned-only for agents), with filtering by status(es), priority, assignee, vendor, free-text search, and pagination/sorting (`Specification`-based dynamic queries).
- **Role-aware field masking**: agents never see `estimatedCost`/`actualCost`; if `app.masking.enabled=true`, agent-visible customer email/phone are masked (`ab****@domain`, `xxxxxx1234` style).
- **Vendor-restricted views**: a vendor viewing their own created ticket cannot see the assigned technician's identity or other service users.
- **Sub-tickets**: a ticket may reference a `parentTicket`; typeahead search endpoints support linking.
- **Comments**: threaded (parent/reply), author-only edit/delete, WebSocket push events (`/queue/ticket-comments`) plus in-app/email/SMS notifications to stakeholders.
- **Site visits**: agents only, requires a valid lat/long, increments `Ticket.siteVisits` counter and appends to `TicketSiteVisit` history.
- **Attachments**: stored on local disk (`app.upload-dir`), served back with role-aware access checks.
- **Business rules enforced in `TicketService`**:
  - Agents cannot set a ticket's status to `CLOSED`.
  - Agents/vendors cannot edit or update a `CLOSED` ticket (`TicketRestController.update`, `ViewController.editTicket`).
  - Vendor-created tickets are auto-assigned `vendorUser = creator`, start unassigned (`assignedTo = null`), and start in `OPEN` status.
  - Vendors can update only tickets they created.
  - `TicketServiceType.LEADS` requires a non-blank `leadFrom` (with an "Others" free-text fallback).
  - Vendor users cannot be added as `serviceUsers`.

### Customer Management
`CustomerRestController` + `CustomerService` + `CustomerAddressService` + `CustomerAddressRepository`.
- Search existing customers by phone/email (derived from ticket history, grouped by phone+email key), with vendor-scoped visibility restrictions.
- Manage reusable `CustomerAddress` records (search, create, fetch-by-id) so repeat customers don't require re-entering address data on every new ticket.

### Technician / Vendor / Staff Management
Handled through the general Admin User Management feature (no separate "technician" entity — technicians are `AppUser`s with `ROLE_AGENT`). Vendor onboarding is a distinct registration flow (`AuthService.registerVendor` / `/api/auth/vendor/register`) requiring company name, contact person, GST number, and phone. ID-proof upload/verification (`UserIdProof`) is primarily used for vendor/agent trust verification (`ViewController.profile` surfaces ID-proof UI only for `ROLE_VENDOR`/`ROLE_AGENT`).

### Billing & Payments
`StaffBillingService` (staff-facing billing rollups) + `TicketPayment`/`TicketPaymentRepository` (per-ticket payment capture, added as part of the June–July 2026 payment-section feature work, see `docs/superpowers/specs/2026-06-12-ticket-payment-section-design.md` and `2026-07-01-staff-billing-payment-integration-design.md`).
- Tickets carry up to three payment records: `CLIENT`, `TECHNICIAN`, `VENDOR` — each with expected price, actual price, payment mode (`CASH`/`BANK_TRANSFER`/`UPI`), payment datetime, and a free-text status.
- **Staff billing rollup** (admin-only, `ADMIN_STAFF_BILLING` feature): for every billable staff member (`ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`) with tickets in `QUOTED`, `RESOLVED`, or `CLOSED` status, aggregates counts/amounts per status. The bill amount per ticket is resolved with a fallback chain: technician payment `actualPrice` → technician payment `expectedPrice` → ticket `actualCost` → ticket `estimatedCost` → zero.
- **Settlement**: a closed ticket's `TicketBillingStatus` (`UNPAID`/`PAID`) can be toggled per-ticket or in bulk per staff member. UI labels these "UnSettled"/"Settled" (relabelled from "Paid" in Aug 2026, see git history `c40d7ca`). "Settled" tickets are surfaced in a separate settled-history section (`b799122`) rather than mixed into the active billing list.
- Invoice generation is a server-rendered view (`admin-staff-billing-invoice.html`) built from the same billing-details data, not a stored PDF/entity.

### Dashboard & Metrics
`DashboardRestController` (redesigned Aug 2026, see `docs/superpowers/specs/2026-08-05-dashboard-card-metrics-redesign.md` and commits `22ef143`…`cb6cf02`).
- **Org metrics** (`/api/dashboard/org-metrics`, `ROLE_ADMIN` only): org-wide ticket counts by every `TicketStatus`, plus counts of enabled technicians (`ROLE_AGENT`) and vendors (`ROLE_VENDOR`).
- **User metrics** (`/api/dashboard/user-metrics`): current user's assigned-ticket counts by status.
- Legacy chart endpoints (`my-ticket-status`, `all-ticket-status`, `user-count`) are retained for backward compatibility with older dashboard chart widgets.

### Chat / Real-time Messaging
`ChatRestController` (history/list via REST) + `ChatWebSocketController` (send/typing via STOMP) + `ChatService`.
- 1:1 conversations, auto-created on first message between two users if none exists.
- Delivery/read receipts (`DELIVERED`/`READ` status pushed over `/queue/chat-status`).
- Typing indicators (`/queue/chat-typing`).
- Messages can optionally reference a `Ticket` for context.
- Unread counts and conversation search supported.

### Notifications
`NotificationRestController` + `NotificationService`. In-app notifications are created for ticket create/update, comments/replies, site visits, chat messages, and account events, then optionally fanned out to email and/or SMS per the `EmailNotificationSetting` matrix (admin-configurable per `EmailNotificationAction`, `EmailNotificationSettingsService`). Admin and (for vendor-created tickets) vendor users get an additional broadcast channel independent of direct ticket-stakeholder notification (`TicketService.notifyAdditionalTicketAudiences`).

### User Profile & Auth
`AuthRestController` + `UserRestController` + `AuthService` + `UserService` + `MobileVerificationService`.
- Registration (agent/vendor/user), email verification, login (email **or** phone identifier, with Indian +91 number normalization), JWT cookie issuance, logout, password reset (request + confirm), self-service password change, profile update, profile picture upload (cropped/resized to 256×256 PNG server-side), mobile OTP send/verify, ID-proof upload/list/view.
- Admin-driven user updates (`AdminRestController.updateUser`) trigger "your account was updated" / "your password was updated" notification emails, and guard against an admin removing their own admin role or disabling their own account.

### Admin Panel
`AdminRestController` + `AdminService` (+ delegating to `UserService`, `StaffBillingService`, `ReportService`). Covers user management, role CRUD, role-feature assignment, email/SMS notification settings, ID-proof review/verification, staff billing, and report generation/download/email — all gated by dedicated `ADMIN_*` features.

### Reports
`ReportService` (932 lines) + `ReportSchedulerService`. Generates Excel (`.xlsx`, Apache POI) reports of two types — `tickets` and `users` — filterable by date range (today/yesterday/last7days/last30days/thismonth/lastmonth/custom) plus ticket status/priority/service type or user status/email-verified/role. Reports can be downloaded, emailed on demand, or auto-generated and emailed daily to admins with `FEATURE_ADMIN_REPORTS` (cron-configurable, `app.reports.schedule.*`).

---

## 5. Workflows

### Registration → Email Verification → Login
1. User registers as `agent`, `vendor`, or `user` type (`POST /api/auth/register`) or via the dedicated vendor form (`POST /api/auth/vendor/register`). Email and phone uniqueness are validated up front.
2. Server creates the `AppUser` with the corresponding role, sends a verification email containing a 24h token link (skipped if `ACCOUNT_VERIFICATION` notification setting is disabled).
3. User visits `/verify-email?token=...` → `GET /api/auth/verify` marks the token used and `AppUser.emailVerified = true`.
4. Login (`POST /api/auth/login` or `/api/auth/vendor/login`, both route through the same `AuthService.doLogin`) requires `emailVerified = true`; on success a JWT is written to the `TM_TOKEN` httpOnly cookie (expiry from `app.jwt.expiration`, default 24h).

### Mobile (Phone) Verification
1. Authenticated user requests an OTP for their registered phone (`POST /api/auth/mobile/send-otp`) — phone must match the user's stored number.
2. 6-digit OTP generated, stored as a `MobileVerificationToken` (10 min expiry), sent via `SmsService`.
3. User submits the OTP (`POST /api/auth/mobile/verify-otp`); on match, `AppUser.phoneVerified = true`.

### Password Reset
1. `POST /api/auth/password-reset` with email → `PasswordResetToken` (2h expiry) created, reset-link email sent (if enabled).
2. User submits new password with the token (`POST /api/auth/password-reset/confirm`) → password updated, token marked used.

### Ticket Creation → Assignment → Resolution → Billing → Settlement
1. **Create**: staff (admin/manager) or a vendor creates a ticket, optionally attaching files and an initial comment, and setting/omitting a technician (`assignedTo`) and additional `serviceUsers`. Vendor-created tickets auto-assign `vendorUser` to the creator and start `OPEN`/unassigned.
2. **Work in progress**: status moves through `LEADS → OPEN → SITE_VISITED/IN_PROGRESS/ON_HOLD/FOLLOW_UP/SITE_REVISIT → QUOTED` as agents log site visits and staff update the ticket. There is no enforced state-machine in code beyond the agent/CLOSED restriction — any authorized actor can set any status value on update.
3. **Resolution**: ticket is set to `RESOLVED`, surfacing it in the admin/manager "Review" queue (`FEATURE_TICKETS_REVIEW`, `/tickets/review`) for a closure or cancellation decision.
4. **Closure**: reviewer sets status to `CLOSED` (or `CANCELLED`). Once `CLOSED`, the ticket becomes billing-relevant and locked from agent/vendor edits.
5. **Billing rollup**: `StaffBillingService` aggregates `QUOTED`/`RESOLVED`/`CLOSED` tickets per assigned staff member for the admin staff-billing dashboard.
6. **Settlement**: admin marks the closed ticket (or all of a staff member's closed tickets) as `PAID` (displayed as "Settled") via `PUT /api/admin/staff-billing/tickets/{ticketId}/billing-status` or `PUT /api/admin/staff-billing/{userId}/status`. Settled tickets move into a separate settled-history view rather than the active unsettled list.

### ID-Proof Verification
1. Vendor/agent uploads a document (`POST /api/users/id-proof`, ≤5MB, image or PDF) → stored with `uploadStatus = PENDING_VERIFICATION`.
2. Admin reviews via `/admin/users/{id}` (backed by `GET /api/admin/users/{id}/id-proofs`) and approves/rejects per document type (`PUT /api/admin/users/{userId}/verify-id-proofs`), setting `verified` + notes.
3. Once a document type is verified, it cannot be re-uploaded by the user.
4. `allIdProofsVerified` in `UserService.getUserDetails` requires **both** an Aadhar Card and a PAN Card to be individually verified — partial verification (e.g. only Aadhar) does not count as fully verified.

### Scheduled Reminders & Digests (`ScheduleReminderService`, cron-driven)
- **Day-before reminder** (default 6 PM daily): emails the assigned user for every non-closed/cancelled/resolved ticket scheduled for tomorrow.
- **On-day reminder** (default 9 AM): same, for tickets scheduled today.
- **Daily open-tickets digest** (default 6 AM): per user, a digest of all their tickets in an "active" status set (`LEADS`…`QUOTED`).
- **Daily admin follow-up digest** (default 6 AM): all admins receive a digest of every ticket currently in `FOLLOW_UP` or `SITE_REVISIT`.
- All four are individually gated by both a global `app.schedule-reminder.enabled` flag and their specific `EmailNotificationAction` setting.

### Scheduled Reporting (`ReportSchedulerService`)
Daily (cron-configurable) generation of the full tickets + users Excel reports, emailed in a single message to every admin with `FEATURE_ADMIN_REPORTS` enabled — skipped entirely if no admin has that feature or reporting is disabled in config.

---

## 6. REST API Reference

All endpoints are under `/api/**` unless noted. Auth is via JWT (cookie or `Authorization: Bearer`) except where marked public. `@PreAuthorize` requirements are listed verbatim where present; endpoints with no listed requirement are open to any authenticated user (or public, if noted).

### Auth — `AuthRestController` (`/api/auth`)
| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/register` | Register as agent/vendor/user | Public |
| POST | `/vendor/register` | Vendor-specific registration form | Public |
| POST | `/login` | Login (email or phone identifier) | Public |
| POST | `/vendor/login` | Vendor login (same logic as `/login`) | Public |
| POST | `/logout` | Clear auth cookie | Public |
| GET | `/verify?token=` | Verify email via token | Public |
| POST | `/password-reset` | Request password reset email | Public |
| POST | `/password-reset/confirm` | Confirm reset with token + new password | Public |
| GET | `/me` | Current user's profile | Authenticated |
| POST | `/mobile/send-otp` | Send phone verification OTP | Authenticated |
| POST | `/mobile/verify-otp` | Verify phone OTP | Authenticated |

### Users — `UserRestController` (`/api/users`)
| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/profile` | Get own profile | Authenticated |
| PATCH | `/profile` | Update own profile | Authenticated |
| POST | `/profile/password` | Change own password | Authenticated |
| POST | `/profile/picture` (multipart) | Upload profile picture (PNG/JPEG, ≤5MB) | Authenticated |
| GET | `/profile/picture` | Get own profile picture | Authenticated |
| GET | `/avatar/{userId}` | Get any user's avatar (SVG placeholder fallback) | Authenticated |
| GET | `/search?query=` | Search users (typeahead for assignment pickers) | Authenticated |
| POST | `/id-proof` (multipart) | Upload an ID-proof document | Authenticated |
| GET | `/id-proof/{docId}/view` | Download own ID-proof document | Authenticated (owner) |
| GET | `/id-proof/list` | List own ID-proof documents | Authenticated |

### Tickets — `TicketRestController` (`/api/tickets`)
| Method | Path | Purpose | Auth (feature authority) |
|---|---|---|---|
| POST | `` (multipart) | Create ticket | `TICKETS_MANAGE`, `TICKETS_CREATE_STANDARD`, or `TICKETS_CREATE_VENDOR` |
| PATCH | `/{ticketId}` (multipart) | Update ticket | `TICKETS_MANAGE` or `SITE_VISIT_EDIT` |
| GET | `` | List tickets (paginated, filterable) | `TICKETS_VIEW` or `TICKETS_CREATED_VIEW` |
| GET | `/search?query=` | Quick ticket search (top 10) | `TICKETS_VIEW` |
| GET | `/parent-search?query=&excludeTicketId=` | Search candidate parent tickets | `TICKETS_VIEW` |
| GET | `/{ticketId}?adminScope=` | Get one ticket | `TICKETS_VIEW` |
| GET | `/{ticketId}/attachments/{attachmentId}` | Download an attachment | `TICKETS_VIEW` |
| DELETE | `/{ticketId}` | Delete ticket | `TICKETS_VIEW` (+ ownership/admin check in service) |
| POST | `/{ticketId}/comments` | Add comment/reply | `TICKETS_VIEW` |
| GET | `/{ticketId}/comments?adminScope=` | List comments (threaded) | `TICKETS_VIEW` |
| PATCH | `/{ticketId}/comments/{commentId}` | Edit own comment | `TICKETS_VIEW` (author only) |
| DELETE | `/{ticketId}/comments/{commentId}` | Delete own comment | `TICKETS_VIEW` (author only) |
| GET | `/{ticketId}/site-visits?adminScope=` | List site visit history | `TICKETS_VIEW` |
| POST | `/{ticketId}/site-visits` | Log a site visit | `TICKETS_MANAGE` or `SITE_VISIT_EDIT` (agent only, enforced in service) |
| GET | `/metrics?adminScope=` | Simple ticket status counts (open/inProgress/pending/resolved/closed) | `TICKETS_VIEW` |

### Customers — `CustomerRestController` (`/api/customers`)
| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/search?query=` | Search customers by phone/email | `TICKETS_CREATE_VENDOR`/`TICKETS_CREATE_STANDARD`/`TICKETS_MANAGE` |
| GET | `/addresses?email=&phone=` | Get a customer's saved addresses | same |
| POST | `/addresses` | Create a new customer address | same |

### Dashboard — `DashboardRestController` (`/api/dashboard`)
| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/org-metrics` | Org-wide status counts + technician/vendor counts | `hasRole('ROLE_ADMIN')` |
| GET | `/user-metrics` | Current user's assigned ticket counts by status | `DASHBOARD_ACCESS` |
| GET | `/my-ticket-status` | Legacy: current user's status chart data | `DASHBOARD_MY_TICKET_STATUS` |
| GET | `/all-ticket-status` | Legacy: org-wide status chart data | `DASHBOARD_ALL_TICKET_STATUS` |
| GET | `/user-count` | Legacy: enabled user counts by role | `DASHBOARD_USER_COUNT` |

### Chat — `ChatRestController` (`/api/chat`) — class-level `@PreAuthorize("hasAuthority('FEATURE_CHAT_ACCESS')")`
| Method | Path | Purpose |
|---|---|---|
| GET | `/conversations?query=` | List conversations (with unread counts, last message) |
| GET | `/conversations/{conversationId}/messages` | Get message history (marks unread as read) |
| POST | `/messages` | Send a message (creates conversation if new) |

### Chat WebSocket — `ChatWebSocketController` (STOMP, endpoint `/ws`, SockJS)
| Destination | Purpose | Auth |
|---|---|---|
| `/app/chat.send` | Send a chat message | `FEATURE_CHAT_ACCESS` |
| `/app/chat.typing` | Broadcast typing indicator | `FEATURE_CHAT_ACCESS` |

Server → client push destinations (via `SimpMessagingTemplate`): `/user/queue/chat` (new message), `/user/queue/chat-status` (delivered/read receipts), `/user/queue/chat-typing` (typing indicator), `/user/queue/notifications` (new notification), `/queue/ticket-comments` (comment add/update/delete event, addressed per-user), `/topic/tickets-refresh` (broadcast ticket list changed), `/topic/admin-users-refresh` (broadcast when a user's status/roles change).

### Notifications — `NotificationRestController` (`/api/notifications`)
| Method | Path | Purpose |
|---|---|---|
| GET | `` | Get unread notifications for current user (also marks them read as a side effect) |
| GET | `/count` | Unread notification count |

### Admin — `AdminRestController` (`/api/admin`)
| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/report` | Basic counts (ticket count, user count) | `ADMIN_REPORT_ACCESS` |
| GET | `/users?query=&role=&enabled=` | List/filter users (paginated) | `ADMIN_USER_MANAGEMENT` |
| PUT | `/users/{userId}` | Update a user (roles, status, password, contact info) | `ADMIN_USER_MANAGEMENT` |
| GET | `/roles` | List roles with user counts | `ADMIN_ROLE_MANAGEMENT` |
| POST | `/roles` | Create role | `ADMIN_ROLE_MANAGEMENT` |
| PUT | `/roles/{roleId}` | Update role | `ADMIN_ROLE_MANAGEMENT` |
| DELETE | `/roles/{roleId}` | Delete role (blocked if any user still holds it) | `ADMIN_ROLE_MANAGEMENT` |
| GET | `/features` | List all `AppFeature`s | `ADMIN_ROLE_FEATURE_ASSIGNMENT` |
| PUT | `/roles/{roleId}/features` | Set a role's feature set | `ADMIN_ROLE_FEATURE_ASSIGNMENT` |
| GET | `/email-notifications` | List notification action settings | `ADMIN_EMAIL_NOTIFICATION_MANAGEMENT` |
| PUT | `/email-notifications` | Update notification action settings | `ADMIN_EMAIL_NOTIFICATION_MANAGEMENT` |
| GET | `/users/{id}` | Get full user details (incl. ID-proof verification flags) | `ADMIN_USER_MANAGEMENT` |
| GET | `/users/{id}/id-proofs` | List a user's ID-proof documents | `ADMIN_USER_MANAGEMENT` |
| PUT | `/users/{userId}/verify-id-proofs` | Approve/reject Aadhar/PAN documents | `ADMIN_USER_MANAGEMENT` |
| GET | `/users/{userId}/id-proof/{idProofType}` | Download a user's ID-proof document | `ADMIN_USER_MANAGEMENT` |
| PUT | `/staff-billing/{userId}/status` | Bulk-mark a staff member's closed tickets paid/unpaid | `ADMIN_STAFF_BILLING` and `ROLE_ADMIN` |
| PUT | `/staff-billing/tickets/{ticketId}/billing-status` | Mark a single closed ticket paid/unpaid | `ADMIN_STAFF_BILLING` and `ROLE_ADMIN` |
| GET | `/reports/download?reportType=&...filters` | Download an Excel report | `ADMIN_REPORTS` |
| POST | `/reports/email?reportType=&recipientEmail=&...` | Email a generated report | `ADMIN_REPORTS` |
| GET | `/reports/recent` | List recently generated reports | `ADMIN_REPORTS` |
| GET | `/reports/download/{reportId}` | Re-download a previously generated report | `ADMIN_REPORTS` |

### Web (Thymeleaf view) routes — `ViewController`
Not REST endpoints, but the page routes the API backs: `/`, `/login`, `/register`, `/vendor/login`, `/vendor/register`, `/dashboard`, `/profile`, `/tickets` (+ `/pending`, `/resolved`, `/created`, `/review`, `/all`), `/tickets/view`, `/tickets/create`, `/tickets/edit`, `/admin` (+ `/users`, `/users/{id}`, `/roles`, `/role-features`, `/email-notifications`, `/staff-billing`, `/staff-billing/{userId}`, `/staff-billing/{userId}/invoice`, `/reports`), `/chat`, `/verify-email`, `/reset-password`, `/access-denied`.

---

## 7. Database Schema Summary

Schema is managed via versioned SQL migrations in `src/main/resources/db/migration/` (`spring.sql.init.mode: never`, no Hibernate auto-DDL in prod). 14 files, evolving as follows:

| Version | Change |
|---|---|
| V1 | Initial schema: `users`, `roles`, `user_roles`, `tickets`, `ticket_comments`, `ticket_attachments`, and related core tables |
| V2 | Add `phone_verified` to `users` |
| V3 | Fix `phone_verified` nullability/backfill for existing rows |
| V4 | Comprehensive re-fix of `phone_verified` (constraint cleanup + backfill, handles edge cases from V2/V3) |
| V5 | Add single-document ID-proof fields directly on `users` (superseded by V8) |
| V6 (`add_cancelled_ticket_status`) | Add `CANCELLED` to the ticket status check constraint |
| V6 (`update_ticket_service_types`) | Data migration: `MAINTENANCE`→`AMC`, `REPAIR`→`SERVICE` on existing tickets. **Note**: two files share the `V6__` version prefix — a Flyway numbering collision worth resolving/renaming if Flyway is enforced strictly. |
| V7 | Comprehensive re-fix of ticket service type values (handles nulls, re-applies V6 mapping) |
| V8 | New `user_id_proofs` table — supports multiple ID-proof documents per user (replaces the single-document V5 columns) |
| V9 | Final cleanup pass on ticket service type values (case-insensitive variants) |
| V10 | Add `QUOTED` to the ticket status enum/constraint |
| V11 | Drop a redundant Hibernate-autogenerated `expires_at` column on `email_verification_tokens` (canonical column is `expiry_date`) |
| V12 | Change `tickets.schedule_date` from `DATE` to `TIMESTAMP` |
| V13 | New `ticket_payments` table + `payment_type`/`payment_mode` enums — backs the Client/Technician/Vendor payment tracking feature |

**Key tables**: `users`, `roles`, `user_roles`, `role_features`, `tickets`, `ticket_comments`, `ticket_attachments`, `ticket_site_visits`, `ticket_payments`, `customer_address`, `user_id_proofs`, `chat_conversations`, `chat_conversation_participants`, `chat_messages`, `notifications`, `email_notification_settings`, `email_verification_tokens`, `mobile_verification_tokens`, `password_reset_tokens`.

Several migration files (V2–V4, V6–V9) show iterative in-production fixes to enum/constraint handling for `phone_verified` and `service_type` — a recurring pain point noted in project memory around Hibernate `@Enumerated(STRING)` columns and stale check constraints; newer enum columns (e.g. `TicketPayment.paymentType`/`paymentMode`, `EmailNotificationSetting.action`) use `columnDefinition = "varchar(N)"` to avoid recurrence.

---

## 8. Configuration & Deployment

**Spring config** (`application.yml`, profiles `dev`/`prod` via `application-dev.yml`/`application-prod.yml`, `SPRING_PROFILES_ACTIVE`):
- `server.port`: 9090
- `app.jwt.secret` (`APP_JWT_SECRET`), `app.jwt.expiration` (ms, default 86400000 = 24h)
- `app.base-url` (`APP_BASE_URL`) — used to build links in emails/SMS
- `app.mail.enabled` / `from-address` / `from-name` (`APP_MAIL_ENABLED`, `APP_MAIL_FROM_ADDRESS`, `APP_MAIL_FROM_NAME`) + standard Spring `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/`MAIL_PASSWORD`/`MAIL_SMTP_AUTH`/`MAIL_STARTTLS`/`MAIL_SSL_ENABLE`
- `app.upload-dir` (`APP_UPLOAD_DIR`) — ticket attachment storage path
- `app.masking.enabled` (`APP_MASKING_ENABLED`) — agent-facing customer email/phone masking
- `app.sms.enabled` / `api-key` / `sender` / `api-url` (`APP_SMS_*`) — SMS gateway (SMS Alert-style HTTP API)
- `app.reports.schedule.enabled` / `cron` (`APP_REPORTS_SCHEDULE_ENABLED`, `APP_REPORTS_SCHEDULE_CRON`)
- `app.schedule-reminder.enabled` / `day-before-cron` / `on-day-cron` / `daily-digest-cron` (`APP_SCHEDULE_REMINDER_*`, `APP_DAILY_DIGEST_CRON`)
- Database (via `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SSLMODE`, `DB_CHANNEL_BINDING`)
- File upload limits: 10MB per file, 25MB per request (`spring.servlet.multipart`)

**Docker / deployment**:
- `Dockerfile`: multi-stage build — `maven:3.9.6-eclipse-temurin-21-alpine` build stage → `eclipse-temurin:21-jre-alpine` runtime, exposes port 9090.
- `docker-compose.yml`: three services —
  - `db`: `postgres:16-alpine`, bound to `127.0.0.1:5432` only, `TZ=Asia/Kolkata`, persistent volume `postgres_data`.
  - `app`: built from the Dockerfile, internal port 9090 (`expose`, not published directly), env-driven config as above, volumes for `/app/uploads` and `/app/logs` mapped to `/opt/ticket_manager_app/*` on the host.
  - `nginx`: `nginx:alpine`, publishes 80/443, reverse-proxies to `app`, mounts Let's Encrypt certs and a certbot webroot, plus a maintenance-page volume.
- Shell scripts at repo root: `deploy.sh`, `rollback.sh`, `restart-app.sh`, `start-app.sh`, `stop-app.sh` — operational tooling for the VPS deployment (not read in detail here; see `DEPLOYMENT.md` for the full runbook).
- `.env` / `prod.env.example` hold the environment variable names above for local/prod setup — no secret values are reproduced in this document.

---

## 9. Change Log / Maintenance Notes

Last generated: **2026-08-20** from a full codebase scan (entities, services, REST controllers, migrations, templates, config, and recent git history/specs).

When adding features, **update the relevant section above in place** rather than appending a changelog entry — keep this document as the current state of truth, not a historical log. In particular:
- New entities → add to §2 in the appropriate group.
- New/changed roles or features → update §3.
- New service-layer business rules or workflows → update §4/§5.
- New or changed REST endpoints → update the relevant controller table in §6.
- New migrations → append a row to the table in §7 and update "Key tables" if new tables are introduced.
- New config/env vars or deployment changes → update §8.
