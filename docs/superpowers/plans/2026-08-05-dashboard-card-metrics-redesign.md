# Dashboard Card Metrics Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the broken dashboard card metrics system with two clean endpoints (org-wide for admin, user-assigned for all roles) and a tab-toggled frontend.

**Architecture:** Two new REST endpoints replace four old ones. The frontend renders a single card grid at a time controlled by a tab toggle (admin only). Non-admin users see only the "My Tickets" grid. Card clicks navigate to `/tickets` with the appropriate filter params, guaranteeing count consistency.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security (`@PreAuthorize`), Thymeleaf, Bootstrap 5 btn-group, vanilla JS fetch.

## Global Constraints

- All 11 `TicketStatus` enum values must appear in every response: `LEADS, OPEN, SITE_VISITED, IN_PROGRESS, ON_HOLD, FOLLOW_UP, SITE_REVISIT, QUOTED, RESOLVED, CLOSED, CANCELLED`
- Org card click → `/tickets?statuses=STATUS&adminScope=true`; User card click → `/tickets?statuses=STATUS&assignedOnly=true`
- Admin sees tab toggle (default: Organization); all other roles see only My Tickets grid, no toggle
- Auto-refresh every 10 seconds must refresh the currently active tab only; clears cache before reload
- Reuse existing CSS classes: `metric-card`, `dashboard-metric-card`, `metric-tone-*`, `metric-card-clickable`
- Existing card HTML template: `<article class="metric-card dashboard-metric-card {tone} metric-card-clickable" data-url="{url}"><div class="dashboard-metric-icon"><i class="{icon}"></i></div><div class="dashboard-metric-copy"><div class="dashboard-metric-title">{title}</div><div class="metric-value">{value}</div></div></article>`

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `src/main/java/com/example/ticketmanager/controller/api/DashboardRestController.java` | Rewrite | Remove 4 old endpoints; add `orgMetrics()` + `userMetrics()` + keep `buildStatusMap()` |
| `src/main/java/com/example/ticketmanager/repository/TicketRepository.java` | Edit | Remove unused `countCreatedByStatus` query |
| `src/main/resources/templates/dashboard.html` | Edit | Replace card metrics HTML section + replace old card metrics JS |

---

### Task 1: Rewrite DashboardRestController

**Files:**
- Modify: `src/main/java/com/example/ticketmanager/controller/api/DashboardRestController.java`

**Interfaces:**
- Consumes: `ticketRepository.countAllByStatus()` → `List<Object[]>` (each element: `[TicketStatus, Long]`)
- Consumes: `ticketRepository.countAssignedByStatus(Long userId)` → `List<Object[]>`
- Consumes: `userRepository.countEnabledUsersByActiveRoleNames(List<String> roleNames)` → `long`
- Consumes: `userService.getByEmail(String email)` → `AppUser`
- Produces: `GET /api/dashboard/org-metrics` → `Map<String,Object>` with keys `statusCounts`, `totalTickets`, `technicianCount`, `vendorCount`
- Produces: `GET /api/dashboard/user-metrics` → `Map<String,Object>` with keys `statusCounts`, `totalTickets`

- [ ] **Step 1: Replace the entire file**

Replace the full contents of `DashboardRestController.java` with:

```java
package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.TicketStatus;
import com.example.ticketmanager.repository.TicketRepository;
import com.example.ticketmanager.repository.UserRepository;
import com.example.ticketmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardRestController {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * Organization-wide ticket counts by status.
     * Includes all tickets in the system (no user assignment filter).
     * Also returns counts of active technicians and vendors.
     * Visible to ROLE_ADMIN only.
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/org-metrics")
    public Map<String, Object> orgMetrics() {
        Map<String, Long> statusCounts = buildStatusMap(ticketRepository.countAllByStatus());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCounts", statusCounts);
        result.put("totalTickets", statusCounts.values().stream().mapToLong(Long::longValue).sum());
        result.put("technicianCount", userRepository.countEnabledUsersByActiveRoleNames(List.of("ROLE_AGENT")));
        result.put("vendorCount", userRepository.countEnabledUsersByActiveRoleNames(List.of("ROLE_VENDOR")));
        return result;
    }

    /**
     * Current user's ticket counts by status (tickets assigned to them).
     * Visible to all roles with FEATURE_DASHBOARD_ACCESS.
     */
    @PreAuthorize("hasAuthority('FEATURE_DASHBOARD_ACCESS')")
    @GetMapping("/user-metrics")
    public Map<String, Object> userMetrics(Principal principal) {
        AppUser user = userService.getByEmail(principal.getName());
        Map<String, Long> statusCounts = buildStatusMap(ticketRepository.countAssignedByStatus(user.getId()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCounts", statusCounts);
        result.put("totalTickets", statusCounts.values().stream().mapToLong(Long::longValue).sum());
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Map<String, Long> buildStatusMap(List<Object[]> rows) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : rows) {
            TicketStatus status = (TicketStatus) row[0];
            long count = ((Number) row[1]).longValue();
            byStatus.put(status.name(), count);
        }
        // Ensure every declared enum status is present (zero-filled if missing)
        Map<String, Long> result = new LinkedHashMap<>();
        for (TicketStatus s : TicketStatus.values()) {
            result.put(s.name(), byStatus.getOrDefault(s.name(), 0L));
        }
        return result;
    }
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS with no errors. If you see a "cannot find symbol" error on `TicketService`, confirm you removed it from the field declarations.

- [ ] **Step 3: Smoke-test the endpoints with curl**

Start the app (`./mvnw spring-boot:run`) and in a separate terminal:

```bash
# Login as admin and grab cookie (adjust credentials to match seeded data)
curl -s -c /tmp/cookies.txt -X POST http://localhost:9090/login \
  -d "username=admin@example.com&password=admin123" -L -o /dev/null -w "%{http_code}"
# Expected: 200

# Test org-metrics (admin only)
curl -s -b /tmp/cookies.txt http://localhost:9090/api/dashboard/org-metrics | python3 -m json.tool
# Expected: JSON with statusCounts (11 keys), totalTickets, technicianCount, vendorCount

# Test user-metrics
curl -s -b /tmp/cookies.txt http://localhost:9090/api/dashboard/user-metrics | python3 -m json.tool
# Expected: JSON with statusCounts (11 keys), totalTickets
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/ticketmanager/controller/api/DashboardRestController.java
git commit -m "feat: replace dashboard metrics endpoints with org-metrics and user-metrics"
```

---

### Task 2: Remove unused countCreatedByStatus from TicketRepository

**Files:**
- Modify: `src/main/java/com/example/ticketmanager/repository/TicketRepository.java`

**Interfaces:**
- Removes: `countCreatedByStatus(@Param("userId") Long userId)` — no longer called by any endpoint

- [ ] **Step 1: Locate and remove the query**

Open `TicketRepository.java`. Find the block around line 278–283:

```java
    @Query("""
            select t.status, count(t.id) from Ticket t
            where t.createdBy.id = :userId
            group by t.status
            """)
    List<Object[]> countCreatedByStatus(@Param("userId") Long userId);
```

Delete the entire `@Query(...)` annotation block and the `List<Object[]> countCreatedByStatus(...)` method declaration. (The exact query text may differ slightly — search for `countCreatedByStatus` to locate it.)

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/ticketmanager/repository/TicketRepository.java
git commit -m "refactor: remove unused countCreatedByStatus repository query"
```

---

### Task 3: Update dashboard.html — HTML section

**Files:**
- Modify: `src/main/resources/templates/dashboard.html`

**Interfaces:**
- Produces HTML: `#orgMetricsGrid` section (admin only, default visible), `#userMetricsGrid` section (all roles, hidden for admin)
- Produces HTML: `#tabOrg` + `#tabUser` buttons (admin only)

- [ ] **Step 1: Replace the card metrics HTML section**

In `dashboard.html`, find the entire `<!-- Card metrics section with admin toggle -->` div block. It starts with:

```html
            <!-- Card metrics section with admin toggle -->
            <div class="mb-4">
```

and ends after the `<section class="metric-grid dashboard-metric-grid" id="dashboardMetrics"></section>` with a closing `</div>`.

Replace that entire block with:

```html
            <!-- Card metrics section -->
            <div class="mb-4">
                <div class="d-flex justify-content-between align-items-center mb-3">
                    <h2 class="h5 mb-0 fw-semibold">Card Metrics</h2>
                    <!-- Admin-only tab toggle -->
                    <div th:if="${currentRoles.contains('ROLE_ADMIN')}" class="btn-group btn-group-sm" role="group">
                        <button id="tabOrg" type="button" class="btn btn-primary" onclick="switchMetricTab('org')">
                            <i class="bi bi-building me-1"></i>Organization
                        </button>
                        <button id="tabUser" type="button" class="btn btn-outline-primary" onclick="switchMetricTab('user')">
                            <i class="bi bi-person me-1"></i>My Tickets
                        </button>
                    </div>
                </div>
                <!-- Org metrics grid: admin only, shown by default -->
                <section th:if="${currentRoles.contains('ROLE_ADMIN')}"
                         id="orgMetricsGrid"
                         class="metric-grid dashboard-metric-grid"></section>
                <!-- User metrics grid: all roles; hidden for admin until tab is clicked -->
                <section id="userMetricsGrid"
                         th:class="${currentRoles.contains('ROLE_ADMIN')} ? 'metric-grid dashboard-metric-grid d-none' : 'metric-grid dashboard-metric-grid'"></section>
            </div>
```

- [ ] **Step 2: Verify the HTML renders**

Start the app and load `/dashboard` as admin. You should see the "Card Metrics" heading with the Organization / My Tickets toggle buttons. The grids will be empty until the JS is added in Task 4. No 500 errors.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/dashboard.html
git commit -m "feat(dashboard): replace card metrics HTML section with org/user tab layout"
```

---

### Task 4: Update dashboard.html — JavaScript

**Files:**
- Modify: `src/main/resources/templates/dashboard.html`

**Interfaces:**
- Removes: `DASHBOARD_METRICS`, `formatMetricValue`, `getMetricCardUrl`, `renderMetricCards`, `getDashboardScope`, `loadDashboardMetrics`, scope toggle DOMContentLoaded listener
- Adds: `STATUS_CARDS`, `ORG_EXTRA_CARDS`, `formatNum`, `buildCardHtml`, `renderCards`, `loadOrgMetrics`, `loadUserMetrics`, `switchMetricTab`, updated `refreshAll`
- `switchMetricTab(tab: 'org'|'user')` → void — toggles tab buttons + grid visibility + lazy-loads on first switch
- `renderCards(containerId, payload, urlBuilder)` → void — injects card HTML into container
- `loadOrgMetrics()` → Promise — fetches `/api/dashboard/org-metrics`, calls `renderCards`
- `loadUserMetrics()` → Promise — fetches `/api/dashboard/user-metrics`, calls `renderCards`

- [ ] **Step 1: Remove old card metrics JavaScript**

In `dashboard.html`, locate and delete the following blocks (search by function name):

1. The `const DASHBOARD_METRICS = [...]` array declaration (13 entries, ends with `];`)
2. The `function formatMetricValue(value) { ... }` function
3. The `function getMetricCardUrl(metric) { ... }` function
4. The `function renderMetricCards(payload) { ... }` function
5. The `function getDashboardScope() { ... }` function
6. The `async function loadDashboardMetrics() { ... }` function
7. The `document.addEventListener('DOMContentLoaded', function() { const scopeToggle = ...` block (scope toggle listener only — do NOT remove other DOMContentLoaded listeners)

- [ ] **Step 2: Add new card metrics JavaScript**

In `dashboard.html`, add the following script block immediately **before** the closing `</script>` tag of the main dashboard script (i.e., just before `refreshAll(); setInterval(refreshAll, 10000);`):

```javascript
// ─── Card Metrics ────────────────────────────────────────────────────────────

const STATUS_CARDS = [
  {title: 'Enquiry',      icon: 'bi bi-megaphone',     statusKey: 'LEADS',        tone: 'metric-tone-enquiry'},
  {title: 'Open',         icon: 'bi bi-folder2-open',  statusKey: 'OPEN',         tone: 'metric-tone-open'},
  {title: 'Site Visited', icon: 'bi bi-geo-alt-fill',  statusKey: 'SITE_VISITED', tone: 'metric-tone-revisit'},
  {title: 'In Progress',  icon: 'bi bi-tools',         statusKey: 'IN_PROGRESS',  tone: 'metric-tone-progress'},
  {title: 'On Hold',      icon: 'bi bi-pause-circle',  statusKey: 'ON_HOLD',      tone: 'metric-tone-hold'},
  {title: 'Follow Up',    icon: 'bi bi-arrow-repeat',  statusKey: 'FOLLOW_UP',    tone: 'metric-tone-followup'},
  {title: 'Site Revisit', icon: 'bi bi-geo-alt',       statusKey: 'SITE_REVISIT', tone: 'metric-tone-revisit'},
  {title: 'Quoted',       icon: 'bi bi-file-text',     statusKey: 'QUOTED',       tone: 'metric-tone-quoted'},
  {title: 'Resolved',     icon: 'bi bi-check2-circle', statusKey: 'RESOLVED',     tone: 'metric-tone-resolved'},
  {title: 'Closed',       icon: 'bi bi-lock',          statusKey: 'CLOSED',       tone: 'metric-tone-closed'},
  {title: 'Cancelled',    icon: 'bi bi-x-circle',      statusKey: 'CANCELLED',    tone: 'metric-tone-cancelled'},
  {title: 'Total Tickets',icon: 'bi bi-ticket-detailed',statusKey: null,           tone: 'metric-tone-total'},
];

const ORG_EXTRA_CARDS = [
  {title: 'Technicians', icon: 'bi bi-person-gear',  valueKey: 'technicianCount', tone: 'metric-tone-users',   url: '/admin/users?role=ROLE_AGENT'},
  {title: 'Vendors',     icon: 'bi bi-person-badge', valueKey: 'vendorCount',     tone: 'metric-tone-vendors', url: '/admin/users?role=ROLE_VENDOR'},
];

let orgData = null;
let userData = null;

function formatNum(v) {
  return new Intl.NumberFormat().format(Number(v || 0));
}

function buildCardHtml(title, icon, value, tone, url) {
  const attr = url ? ` data-url="${url}"` : '';
  const cls  = url ? ' metric-card-clickable' : '';
  return `<article class="metric-card dashboard-metric-card ${tone}${cls}"${attr}>
    <div class="dashboard-metric-icon"><i class="${icon}"></i></div>
    <div class="dashboard-metric-copy">
      <div class="dashboard-metric-title">${title}</div>
      <div class="metric-value">${formatNum(value)}</div>
    </div>
  </article>`;
}

function renderCards(containerId, payload, urlBuilder) {
  const container = document.getElementById(containerId);
  if (!container) return;
  const statusCounts = payload.statusCounts || {};

  const statusHtml = STATUS_CARDS.map(card => {
    const value = card.statusKey ? (statusCounts[card.statusKey] ?? 0) : (payload.totalTickets ?? 0);
    const url   = card.statusKey ? urlBuilder(card.statusKey) : urlBuilder(null);
    return buildCardHtml(card.title, card.icon, value, card.tone, url);
  }).join('');

  const extraHtml = (containerId === 'orgMetricsGrid' ? ORG_EXTRA_CARDS : []).map(card => {
    return buildCardHtml(card.title, card.icon, payload[card.valueKey] ?? 0, card.tone, card.url);
  }).join('');

  container.innerHTML = statusHtml + extraHtml;

  container.querySelectorAll('[data-url]').forEach(card => {
    card.addEventListener('click', () => { window.location.href = card.dataset.url; });
  });
}

async function loadOrgMetrics() {
  const container = document.getElementById('orgMetricsGrid');
  if (!container) return;
  try {
    const res = await fetch('/api/dashboard/org-metrics');
    if (!res.ok) return;
    orgData = await res.json();
    renderCards('orgMetricsGrid', orgData, sk => sk
      ? `/tickets?statuses=${sk}&adminScope=true`
      : '/tickets?adminScope=true');
  } catch (e) { console.warn('Org metrics load failed', e); }
}

async function loadUserMetrics() {
  const container = document.getElementById('userMetricsGrid');
  if (!container) return;
  try {
    const res = await fetch('/api/dashboard/user-metrics');
    if (!res.ok) return;
    userData = await res.json();
    renderCards('userMetricsGrid', userData, sk => sk
      ? `/tickets?statuses=${sk}&assignedOnly=true`
      : '/tickets?assignedOnly=true');
  } catch (e) { console.warn('User metrics load failed', e); }
}

function switchMetricTab(tab) {
  const orgGrid  = document.getElementById('orgMetricsGrid');
  const userGrid = document.getElementById('userMetricsGrid');
  const tabOrg   = document.getElementById('tabOrg');
  const tabUser  = document.getElementById('tabUser');

  if (tab === 'org') {
    orgGrid  && orgGrid.classList.remove('d-none');
    userGrid && userGrid.classList.add('d-none');
    tabOrg  && (tabOrg.className  = 'btn btn-primary btn-sm');
    tabUser && (tabUser.className = 'btn btn-outline-primary btn-sm');
    if (!orgData) loadOrgMetrics();
  } else {
    orgGrid  && orgGrid.classList.add('d-none');
    userGrid && userGrid.classList.remove('d-none');
    tabOrg  && (tabOrg.className  = 'btn btn-outline-primary btn-sm');
    tabUser && (tabUser.className = 'btn btn-primary btn-sm');
    if (!userData) loadUserMetrics();
  }
}
```

- [ ] **Step 3: Update refreshAll to use new loaders**

In `dashboard.html`, find the `async function refreshAll()` (or `function refreshAll()`). Replace **only** the `loadDashboardMetrics()` call inside it with:

```javascript
    // Reset caches so auto-refresh always fetches fresh data
    orgData = null;
    userData = null;
    // Reload whichever tab is currently active
    const orgGrid = document.getElementById('orgMetricsGrid');
    if (orgGrid && !orgGrid.classList.contains('d-none')) {
      loadOrgMetrics();
    } else {
      loadUserMetrics();
    }
```

Leave all other calls inside `refreshAll` (`loadMyTicketStatus`, `loadAllTicketStatus`, `loadUserCount`) untouched.

- [ ] **Step 4: Add initial load call**

Below the `refreshAll(); setInterval(refreshAll, 10000);` block at the bottom of the script, ensure the correct metrics load on first page visit. The `refreshAll()` call already handles this via the logic in Step 3 — no extra call needed. Verify by inspecting that `orgMetricsGrid` is not `d-none` by default for admin (it isn't per the Thymeleaf HTML), so `refreshAll()` will call `loadOrgMetrics()` automatically.

- [ ] **Step 5: Manual verification — Admin user**

1. Load `/dashboard` as admin. Card Metrics section should show "Organization" (active, filled) and "My Tickets" (inactive) buttons.
2. Org grid should show 14 cards (11 statuses + Total Tickets + Technicians + Vendors) with counts.
3. Click "My Tickets" tab — org grid hides, user grid appears with 12 cards (11 statuses + Total Tickets).
4. Click any status card (e.g. "Open") in org tab → should navigate to `/tickets?statuses=OPEN&adminScope=true` and ticket list count must match the card count.
5. Click any status card in My Tickets tab → should navigate to `/tickets?statuses=OPEN&assignedOnly=true` and count must match.
6. Wait 10 seconds — grids should auto-refresh with fresh data (counts update if you created a ticket in another tab).

- [ ] **Step 6: Manual verification — Non-admin user**

1. Load `/dashboard` as a non-admin (manager, agent, or vendor). No tab toggle should appear.
2. Only "My Tickets" grid is shown with 12 cards.
3. Click a status card → navigates to `/tickets?statuses=STATUS&assignedOnly=true`, count matches.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/templates/dashboard.html
git commit -m "feat(dashboard): implement org/user card metrics with tab toggle and clickable navigation"
```
