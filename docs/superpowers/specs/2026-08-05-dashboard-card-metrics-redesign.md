# Dashboard Card Metrics Redesign

**Date:** 2026-08-05
**Status:** Approved
**Scope:** Remove all existing dashboard card metrics (backend + frontend) and replace with a clean two-view implementation.

---

## Problem

The existing `/api/dashboard/metrics` endpoint accumulated complex conditional logic (scope params, visibleCards sets, vendor/admin/agent branching) that caused count mismatches between the card display and the filtered tickets page. A clean replacement is warranted.

---

## Solution Overview

Two separate REST endpoints with a single responsibility each. Frontend renders one card grid at a time, toggled by a tab control (admin only).

---

## Backend

### Endpoints to Remove

All 4 existing endpoints in `DashboardRestController`:
- `GET /api/dashboard/my-ticket-status`
- `GET /api/dashboard/all-ticket-status`
- `GET /api/dashboard/user-count`
- `GET /api/dashboard/metrics`

Also remove the unused `countCreatedByStatus(@Param userId)` repository query from `TicketRepository` (no longer called by any endpoint).

### New Endpoints

#### `GET /api/dashboard/org-metrics`
- **Auth:** `@PreAuthorize("hasRole('ROLE_ADMIN')")`
- **Logic:**
  - Count all tickets grouped by status using `ticketRepository.countAllByStatus()` — no user assignment filter
  - Count active technicians: users with role `ROLE_AGENT` (enabled only)
  - Count active vendors: users with role `ROLE_VENDOR` (enabled only)
- **Response:**
```json
{
  "statusCounts": {
    "LEADS": 3,
    "OPEN": 12,
    "SITE_VISITED": 2,
    "IN_PROGRESS": 7,
    "ON_HOLD": 4,
    "FOLLOW_UP": 1,
    "SITE_REVISIT": 2,
    "QUOTED": 5,
    "RESOLVED": 8,
    "CLOSED": 20,
    "CANCELLED": 1
  },
  "totalTickets": 65,
  "technicianCount": 5,
  "vendorCount": 3
}
```

#### `GET /api/dashboard/user-metrics`
- **Auth:** `@PreAuthorize("hasAuthority('FEATURE_DASHBOARD_ACCESS')")`
- **Logic:**
  - Count tickets assigned to the current user grouped by status using `ticketRepository.countAssignedByStatus(userId)`
  - All roles (admin, manager, agent, vendor) — uniform: assigned-to-user counts
- **Response:**
```json
{
  "statusCounts": {
    "LEADS": 1,
    "OPEN": 4,
    "SITE_VISITED": 0,
    "IN_PROGRESS": 2,
    "ON_HOLD": 1,
    "FOLLOW_UP": 0,
    "SITE_REVISIT": 0,
    "QUOTED": 1,
    "RESOLVED": 3,
    "CLOSED": 5,
    "CANCELLED": 0
  },
  "totalTickets": 17
}
```

Both endpoints use the existing `buildStatusMap()` helper to ensure all 11 enum values are present (zero-filled if no tickets).

---

## Frontend (`dashboard.html`)

### What Gets Removed

- The entire `<div class="mb-4">` card metrics section (including the admin scope toggle)
- All related JavaScript:
  - `DASHBOARD_METRICS` array
  - `getMetricCardUrl()` function
  - `renderMetricCards()` function
  - `getDashboardScope()` function
  - The `/api/dashboard/metrics` fetch call and its handler

### New HTML Structure

```html
<div class="mb-4">
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h2 class="h5 mb-0 fw-semibold">Card Metrics</h2>
    <!-- Admin-only tab toggle -->
    <div th:if="${currentRoles.contains('ROLE_ADMIN')}" class="btn-group btn-group-sm">
      <button id="tabOrg" class="btn btn-primary" onclick="switchMetricTab('org')">
        <i class="bi bi-building me-1"></i>Organization
      </button>
      <button id="tabUser" class="btn btn-outline-primary" onclick="switchMetricTab('user')">
        <i class="bi bi-person me-1"></i>My Tickets
      </button>
    </div>
  </div>

  <!-- Org metrics grid (admin only, default visible) -->
  <section th:if="${currentRoles.contains('ROLE_ADMIN')}"
           id="orgMetricsGrid" class="metric-grid dashboard-metric-grid"></section>

  <!-- User metrics grid (all roles; hidden for admin until toggled) -->
  <section id="userMetricsGrid"
           th:classappend="${currentRoles.contains('ROLE_ADMIN')} ? 'd-none' : ''"
           class="metric-grid dashboard-metric-grid"></section>
</div>
```

### Card Definition

All 11 statuses + totals, rendered identically in both grids:

| Key | Title | Icon | Status Key | Tone CSS |
|-----|-------|------|------------|----------|
| leads | Enquiry | `bi-megaphone` | `LEADS` | `metric-tone-enquiry` |
| open | Open | `bi-folder2-open` | `OPEN` | `metric-tone-open` |
| siteVisited | Site Visited | `bi-geo-alt-fill` | `SITE_VISITED` | `metric-tone-revisit` |
| inProgress | In Progress | `bi-tools` | `IN_PROGRESS` | `metric-tone-progress` |
| onHold | On Hold | `bi-pause-circle` | `ON_HOLD` | `metric-tone-hold` |
| followUp | Follow Up | `bi-arrow-repeat` | `FOLLOW_UP` | `metric-tone-followup` |
| siteRevisit | Site Revisit | `bi-geo-alt` | `SITE_REVISIT` | `metric-tone-revisit` |
| quoted | Quoted | `bi-file-text` | `QUOTED` | `metric-tone-quoted` |
| resolved | Resolved | `bi-check2-circle` | `RESOLVED` | `metric-tone-resolved` |
| closed | Closed | `bi-lock` | `CLOSED` | `metric-tone-closed` |
| cancelled | Cancelled | `bi-x-circle` | `CANCELLED` | `metric-tone-cancelled` |
| totalTickets | Total Tickets | `bi-ticket-detailed` | — | `metric-tone-total` |

Org-only extra cards:

| Key | Title | Icon | Tone CSS | Click URL |
|-----|-------|------|----------|-----------|
| technicians | Technicians | `bi-person-gear` | `metric-tone-users` | `/admin/users?role=ROLE_AGENT` |
| vendors | Vendors | `bi-person-badge` | `metric-tone-vendors` | `/admin/users?role=ROLE_VENDOR` |

### Card Click Navigation

| Tab | Card type | Destination URL |
|-----|-----------|-----------------|
| Organization | Status card | `/tickets?statuses=STATUS&adminScope=true` |
| Organization | Total Tickets | `/tickets?adminScope=true` |
| Organization | Technicians | `/admin/users?role=ROLE_AGENT` |
| Organization | Vendors | `/admin/users?role=ROLE_VENDOR` |
| My Tickets (any role) | Status card | `/tickets?statuses=STATUS&assignedOnly=true` |
| My Tickets (any role) | Total Tickets | `/tickets?assignedOnly=true` |

### New JavaScript

```
ORG_STATUS_CARDS   — array of {key, title, icon, statusKey, tone}
USER_STATUS_CARDS  — same array (same 11 statuses + total)

loadOrgMetrics()   — fetches /api/dashboard/org-metrics, calls renderCards('org', payload)
loadUserMetrics()  — fetches /api/dashboard/user-metrics, calls renderCards('user', payload)
renderCards(view, payload) — builds card HTML, injects into correct container
switchMetricTab(tab) — toggles btn-group active state, shows/hides grids, lazy-loads if needed
buildOrgCardUrl(statusKey) — returns /tickets?statuses=X&adminScope=true
buildUserCardUrl(statusKey) — returns /tickets?statuses=X&assignedOnly=true
```

Cache: each endpoint response stored in a module-scoped variable (`orgData`, `userData`). Second tab switch reuses cached response.

On page load:
- Admin: call `loadOrgMetrics()` immediately; `loadUserMetrics()` deferred until user tab is first clicked
- Non-admin: call `loadUserMetrics()` immediately

---

## Consistency Guarantee

The ticket counts shown on cards will match the filtered tickets page because:
- Org cards use `adminScope=true` — tickets page uses the same flag to show all tickets unrestricted
- User cards use `assignedOnly=true` — tickets page uses the same flag to filter by assigned user
- Both card count queries use the same repository methods the tickets API uses

---

## Files Changed

| File | Action |
|------|--------|
| `DashboardRestController.java` | Remove all 4 endpoints, add 2 new ones |
| `TicketRepository.java` | Remove `countCreatedByStatus` query (unused) |
| `dashboard.html` | Replace card metrics section + JS |
