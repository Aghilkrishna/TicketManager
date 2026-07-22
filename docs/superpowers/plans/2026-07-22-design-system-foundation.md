# Design System Foundation (Phase 0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retint TicketManager's shared CSS/HTML foundation (`app.css`, `fragments/layout.html`) from the current blue/teal glass-panel look to the approved "Indigo Modern SaaS" design system (indigo primary, emerald accent, Inter/Manrope typography), so every screen shifts to the new visual language at once, before any screen-specific redesign work begins.

**Architecture:** Pure CSS custom-property and CDN-link changes to two existing files — `src/main/resources/static/css/app.css` and `src/main/resources/templates/fragments/layout.html`. No new files, no build step, no JS changes, no template markup changes beyond one `<link>` addition. Every existing Bootstrap/Bootstrap Icons class and role/feature-gated nav element keeps working exactly as before; only colors, fonts, and a handful of component-level Bootstrap CSS variables change.

**Tech Stack:** Thymeleaf templates, Bootstrap 5.3.3 (CDN), Bootstrap Icons 1.11.3 (CDN), custom `app.css` (CSS custom properties, no preprocessor), vanilla `app.js` (unchanged).

## Global Constraints

- No backend/API/controller/entity changes of any kind (spec Non-Goals).
- Keep Bootstrap 5 as the structural/utility layer — reskin via CSS variables and overrides only, no class-usage rewrites in templates (spec: "Keep Bootstrap, reskin").
- Keep Bootstrap Icons (`bi bi-*`) — no icon library swap.
- Keep the sidebar + topbar shell structure and all `currentFeatures.contains(...)` / `currentRoles.contains(...)` gating in `fragments/layout.html` untouched — visual changes only.
- No new build tooling, no JS framework, no automated frontend test suite — verification is manual, in-browser (spec Verification Approach).
- Body text contrast must remain ≥ 4.5:1 (WCAG AA) against its background after retinting.
- Every new CSS custom property follows the naming already established in the approved spec (`--primary`, `--accent`, `--status-*`, `--priority-*`, `--space-*`, etc.) — do not invent parallel names.

---

### Task 1: Design tokens + Bootstrap utility bridge

**Files:**
- Modify: `src/main/resources/static/css/app.css:1-14` (the `:root` block)

**Interfaces:**
- Produces: the full token set every later task and every future screen-redesign task consumes — `--primary`, `--primary-hover`, `--primary-active`, `--primary-soft`, `--accent`, `--accent-soft`, `--warning`, `--warning-soft`, `--danger`, `--danger-soft`, `--bg`, `--panel`, `--panel-strong`, `--border`, `--text`, `--muted`, `--shadow`, `--shadow-flat`, `--shadow-card`, `--shadow-modal`, `--radius-control`, `--radius-card`, `--space-1`..`--space-8`, `--duration-fast`, `--duration-base`, `--ease-out`, `--font-sans`, `--font-heading`, `--status-neutral-bg/-fg`, `--status-progress-bg/-fg`, `--status-waiting-bg/-fg`, `--status-positive-bg/-fg`, `--status-negative-bg/-fg`, `--priority-low/medium/high/critical-bg/-fg`, and the Bootstrap bridge vars `--bs-primary(-rgb)`, `--bs-success(-rgb)`, `--bs-warning(-rgb)`, `--bs-danger(-rgb)`, `--bs-info(-rgb)`, `--bs-link-color(-rgb)`, `--bs-link-hover-color(-rgb)`.

- [ ] **Step 1: Confirm the current block matches exactly**

Run: `sed -n '1,14p' src/main/resources/static/css/app.css`

Expected output (must match before editing — if it doesn't, stop and re-read the file, do not blind-edit):

```css
:root {
    --bg: #f3f6fb;
    --panel: rgba(255, 255, 255, 0.86);
    --panel-strong: #ffffff;
    --border: rgba(23, 43, 77, 0.08);
    --text: #172b4d;
    --muted: #667085;
    --primary: #0f6cbd;
    --primary-soft: #d9ecff;
    --accent: #14b8a6;
    --warning: #f59e0b;
    --danger: #ef4444;
    --shadow: 0 22px 60px rgba(15, 23, 42, 0.08);
}
```

- [ ] **Step 2: Replace the block with the full token set**

Replace the exact block from Step 1 with:

```css
:root {
    /* Brand */
    --primary: #6366F1;
    --primary-hover: #4F46E5;
    --primary-active: #4338CA;
    --primary-soft: #EEF0FF;
    --accent: #059669;
    --accent-soft: #D1FAE5;
    --warning: #F59E0B;
    --warning-soft: #FEF3C7;
    --danger: #DC2626;
    --danger-soft: #FEE2E2;

    /* Surfaces & text */
    --bg: #F5F3FF;
    --panel: rgba(255, 255, 255, 0.86);
    --panel-strong: #ffffff;
    --border: rgba(76, 29, 149, 0.10);
    --text: #1E1B4B;
    --muted: #64748B;
    --shadow: 0 22px 60px rgba(49, 46, 129, 0.10);

    /* Elevation scale */
    --shadow-flat: none;
    --shadow-card: 0 1px 3px rgba(30, 27, 75, 0.08), 0 1px 2px rgba(30, 27, 75, 0.06);
    --shadow-modal: 0 20px 40px rgba(30, 27, 75, 0.16);

    /* Radius scale */
    --radius-control: 8px;
    --radius-card: 14px;

    /* Spacing scale (4px steps) */
    --space-1: 4px;
    --space-2: 8px;
    --space-3: 12px;
    --space-4: 16px;
    --space-5: 20px;
    --space-6: 24px;
    --space-7: 28px;
    --space-8: 32px;

    /* Motion */
    --duration-fast: 150ms;
    --duration-base: 200ms;
    --ease-out: cubic-bezier(0, 0, 0.2, 1);

    /* Typography */
    --font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    --font-heading: 'Manrope', 'Inter', -apple-system, sans-serif;

    /* Status semantic groups (TicketStatus) */
    --status-neutral-bg: #E0F2FE;
    --status-neutral-fg: #0369A1;
    --status-progress-bg: #E0E7FF;
    --status-progress-fg: #4338CA;
    --status-waiting-bg: #FEF3C7;
    --status-waiting-fg: #B45309;
    --status-positive-bg: #D1FAE5;
    --status-positive-fg: #047857;
    --status-negative-bg: #FEE2E2;
    --status-negative-fg: #B91C1C;

    /* Priority scale (TicketPriority), neutral -> danger */
    --priority-low-bg: #E0F2FE;
    --priority-low-fg: #0369A1;
    --priority-medium-bg: #FEF3C7;
    --priority-medium-fg: #B45309;
    --priority-high-bg: #FFEDD5;
    --priority-high-fg: #C2410C;
    --priority-critical-bg: #FEE2E2;
    --priority-critical-fg: #B91C1C;

    /* Bootstrap 5.3 utility bridge — bg-*/text-*/border-*/link-* utilities read these */
    --bs-primary: #6366F1;
    --bs-primary-rgb: 99, 102, 241;
    --bs-success: #059669;
    --bs-success-rgb: 5, 150, 105;
    --bs-warning: #F59E0B;
    --bs-warning-rgb: 245, 158, 11;
    --bs-danger: #DC2626;
    --bs-danger-rgb: 220, 38, 38;
    --bs-info: #0EA5E9;
    --bs-info-rgb: 14, 165, 233;
    --bs-link-color: #6366F1;
    --bs-link-color-rgb: 99, 102, 241;
    --bs-link-hover-color: #4F46E5;
    --bs-link-hover-color-rgb: 79, 70, 229;
}
```

- [ ] **Step 3: Verify no syntax breakage and old primary/accent hex are gone from the token block**

Run: `sed -n '1,90p' src/main/resources/static/css/app.css | grep -c "^}"`
Expected: at least `1` (the `:root` block closes cleanly) and no shell/CSS parse errors when the app is later loaded in-browser (checked in Task 7).

Run: `sed -n '1,90p' src/main/resources/static/css/app.css | grep -E "0f6cbd|14b8a6"`
Expected: no output (old primary/accent hex no longer in the token block — they still exist elsewhere in the file until Task 4 runs, which is expected at this point).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/app.css
git commit -m "feat(design-system): replace root tokens with Indigo Modern SaaS palette"
```

---

### Task 2: Bootstrap component overrides (buttons, form focus, checkboxes)

Bootstrap 5.3 utility classes (`.bg-primary`, `.text-primary`, etc.) read the `--bs-*` bridge from Task 1 automatically, but `.btn-primary`, `.btn-outline-primary`, `.btn-success`, `.btn-outline-success`, `.form-control:focus`, `.form-select:focus`, and `.form-check-input:checked/:focus` set their own component-scoped Bootstrap CSS variables with hardcoded hex values at Bootstrap's build time — they do **not** reference `--bs-primary` internally, so Task 1 alone won't retint them. This task overrides those component variables directly, using the same variable names Bootstrap already defines, which is the standard Bootstrap 5.3 CSS-variable theming technique (no Sass recompile needed).

**Files:**
- Modify: `src/main/resources/static/css/app.css` (insert new rules immediately after the `:root` block from Task 1, before the existing `* { box-sizing: border-box; }` rule)

**Interfaces:**
- Consumes: `--primary`, `--primary-hover`, `--primary-active`, `--accent` from Task 1.
- Produces: retinted `.btn-primary`, `.btn-outline-primary`, `.btn-success`, `.btn-outline-success`, `.form-control:focus`, `.form-select:focus`, `.form-check-input:checked`, `.form-check-input:focus` — used as-is by all 14 screens via existing Bootstrap markup, no template changes needed.

- [ ] **Step 1: Confirm the insertion point**

Run: `sed -n '14,20p' src/main/resources/static/css/app.css`

Expected (after Task 1, the `:root` block's closing `}` is immediately followed by a blank line and `* { box-sizing: border-box; }`):

```css
}

* {
    box-sizing: border-box;
}
```

- [ ] **Step 2: Insert the Bootstrap component override block**

Insert the following immediately after the `:root` block's closing `}` and before `* { box-sizing: border-box; }`:

```css

.btn-primary {
    --bs-btn-color: #fff;
    --bs-btn-bg: var(--primary);
    --bs-btn-border-color: var(--primary);
    --bs-btn-hover-color: #fff;
    --bs-btn-hover-bg: var(--primary-hover);
    --bs-btn-hover-border-color: var(--primary-hover);
    --bs-btn-focus-shadow-rgb: 99, 102, 241;
    --bs-btn-active-color: #fff;
    --bs-btn-active-bg: var(--primary-active);
    --bs-btn-active-border-color: var(--primary-active);
    --bs-btn-disabled-bg: var(--primary);
    --bs-btn-disabled-border-color: var(--primary);
}

.btn-outline-primary {
    --bs-btn-color: var(--primary);
    --bs-btn-border-color: var(--primary);
    --bs-btn-hover-color: #fff;
    --bs-btn-hover-bg: var(--primary);
    --bs-btn-hover-border-color: var(--primary);
    --bs-btn-focus-shadow-rgb: 99, 102, 241;
    --bs-btn-active-color: #fff;
    --bs-btn-active-bg: var(--primary);
    --bs-btn-active-border-color: var(--primary);
    --bs-btn-disabled-color: var(--primary);
    --bs-btn-disabled-border-color: var(--primary);
}

.btn-success {
    --bs-btn-color: #fff;
    --bs-btn-bg: var(--accent);
    --bs-btn-border-color: var(--accent);
    --bs-btn-hover-color: #fff;
    --bs-btn-hover-bg: #047857;
    --bs-btn-hover-border-color: #047857;
    --bs-btn-focus-shadow-rgb: 5, 150, 105;
    --bs-btn-active-color: #fff;
    --bs-btn-active-bg: #047857;
    --bs-btn-active-border-color: #047857;
    --bs-btn-disabled-bg: var(--accent);
    --bs-btn-disabled-border-color: var(--accent);
}

.btn-outline-success {
    --bs-btn-color: var(--accent);
    --bs-btn-border-color: var(--accent);
    --bs-btn-hover-color: #fff;
    --bs-btn-hover-bg: var(--accent);
    --bs-btn-hover-border-color: var(--accent);
    --bs-btn-focus-shadow-rgb: 5, 150, 105;
    --bs-btn-active-color: #fff;
    --bs-btn-active-bg: var(--accent);
    --bs-btn-active-border-color: var(--accent);
    --bs-btn-disabled-color: var(--accent);
    --bs-btn-disabled-border-color: var(--accent);
}

.form-control:focus,
.form-select:focus {
    border-color: var(--primary);
    box-shadow: 0 0 0 0.25rem rgba(99, 102, 241, 0.25);
}

.form-check-input:checked {
    background-color: var(--primary);
    border-color: var(--primary);
}

.form-check-input:focus {
    border-color: var(--primary);
    box-shadow: 0 0 0 0.25rem rgba(99, 102, 241, 0.25);
}
```

- [ ] **Step 3: Verify the rules were inserted correctly**

Run: `grep -n "^\.btn-primary {$\|^\.btn-outline-primary {$\|^\.btn-success {$\|^\.form-check-input:checked {$" src/main/resources/static/css/app.css`

Expected: four matches, all appearing before line ~90 (i.e., before `.sidebar {`), confirming the block landed in the right place and wasn't duplicated elsewhere.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/app.css
git commit -m "feat(design-system): retint Bootstrap buttons and form focus states to indigo/emerald"
```

---

### Task 3: Typography — Inter + Manrope

**Files:**
- Modify: `src/main/resources/templates/fragments/layout.html:4-11` (the `head(title)` fragment)
- Modify: `src/main/resources/static/css/app.css` (the `body` rule, and a new heading-font rule)

**Interfaces:**
- Consumes: `--font-sans`, `--font-heading` from Task 1.
- Produces: every screen's body text renders in Inter and every heading-level element/heading-style class renders in Manrope, with no per-screen template changes needed since `fragments/layout.html :: head` is shared by all 14 screens.

- [ ] **Step 1: Confirm current head fragment**

Run: `sed -n '4,11p' src/main/resources/templates/fragments/layout.html`

Expected:

```html
<th:block th:fragment="head(title)">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title th:text="${title}">Ticket Manager</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css" rel="stylesheet">
    <link th:href="@{/css/app.css}" rel="stylesheet">
</th:block>
```

- [ ] **Step 2: Add Google Fonts links before the `app.css` link**

Replace:

```html
    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css" rel="stylesheet">
    <link th:href="@{/css/app.css}" rel="stylesheet">
</th:block>
```

with:

```html
    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.css" rel="stylesheet">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Manrope:wght@600;700;800&display=swap" rel="stylesheet">
    <link th:href="@{/css/app.css}" rel="stylesheet">
</th:block>
```

- [ ] **Step 3: Apply the fonts in `app.css`**

Run: `grep -n "^body {" src/main/resources/static/css/app.css`

Expected: one match (the `body` rule that currently sets `min-height`, `margin`, `color`, `background`).

Add `font-family: var(--font-sans);` as the first declaration inside that `body {` rule, and immediately after the `body { ... }` rule's closing `}`, add a new rule:

```css
h1, h2, h3, h4, h5, h6,
.page-title, .brand-title, .metric-value {
    font-family: var(--font-heading);
}
```

- [ ] **Step 4: Verify**

Run: `grep -n "font-family" src/main/resources/static/css/app.css`

Expected: two matches — the `body` rule using `var(--font-sans)` and the new heading rule using `var(--font-heading)`.

Run: `grep -n "fonts.googleapis.com/css2" src/main/resources/templates/fragments/layout.html`

Expected: one match, the new stylesheet link.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/css/app.css src/main/resources/templates/fragments/layout.html
git commit -m "feat(design-system): add Inter/Manrope typography via Google Fonts"
```

---

### Task 4: Retint hardcoded brand-color gradients and tints

The tokens in Task 1 only retint CSS that already references `var(--primary)` / `var(--accent)`. Several rules in `app.css` hardcode the *old* hex/rgba values directly (gradients, icon tiles, alert tints, scrollbar) — these must be swept to the new palette so every screen shifts together, per the spec's Rollout Mechanics section (a coherent shift, not a half-old/half-new app).

**Files:**
- Modify: `src/main/resources/static/css/app.css` (multiple non-adjacent rules — see steps)

**Interfaces:**
- Consumes: no new tokens (these become literal new hex/rgba values matching the palette, consistent with how the originals were also literal).
- Produces: no new selectors — existing selectors (`.sidebar`, `.topbar`, `.hero-banner`, `.site-visit-card::before`, `.autosuggest-avatar.ticket-avatar`, `.autosuggest-action`, `.auth-banner`, `.metric-tone-total .dashboard-metric-icon`, `.app-alert*`, `.app-inline-alert*`, `body`, `.chat-action-btn`, `.chat-messages`, scrollbar thumbs) simply render in the new palette.

- [ ] **Step 1: Global replace — old accent (teal) rgba tint → new accent (emerald) rgba tint**

Run first to see every occurrence that will change:
`grep -n "rgba(20, 184, 166," src/main/resources/static/css/app.css`

Expected: 5 matches (body background gradient, `.app-alert-success` border, `.app-alert-success .app-alert-icon` background, `.app-inline-alert-success` border, `.app-inline-alert-success .app-inline-alert-icon` background).

In each of those 5 lines, replace `rgba(20, 184, 166,` with `rgba(5, 150, 105,` (keep each line's existing opacity value unchanged — only the RGB triplet changes).

Verify: `grep -c "rgba(20, 184, 166," src/main/resources/static/css/app.css` → expected `0`.
Verify: `grep -c "rgba(5, 150, 105," src/main/resources/static/css/app.css` → expected `5`.

- [ ] **Step 2: Global replace — old primary (blue) rgba tint → new primary (indigo) rgba tint**

Run first: `grep -n "rgba(15, 108, 189," src/main/resources/static/css/app.css`

Expected: 10 matches (body background gradient, `.autosuggest-action` background, `.app-alert-info` border, `.app-alert-info .app-alert-icon` background, `.app-inline-alert-info` border, `.app-inline-alert-info .app-inline-alert-icon` background, `.chat-action-btn` background, `.chat-messages` background gradient, and 2 scrollbar-thumb rules).

In each of those 10 lines, replace `rgba(15, 108, 189,` with `rgba(99, 102, 241,` (keep each line's existing opacity value unchanged).

Verify: `grep -c "rgba(15, 108, 189," src/main/resources/static/css/app.css` → expected `0`.
Verify: `grep -c "rgba(99, 102, 241," src/main/resources/static/css/app.css` → expected `10`.

- [ ] **Step 3: Sidebar + topbar dark gradient → indigo-tinted dark**

Run: `grep -n "linear-gradient(180deg, #0f172a, #132238);" src/main/resources/static/css/app.css`

Expected: 2 matches (`.sidebar` and `.topbar`).

Replace both occurrences of `background: linear-gradient(180deg, #0f172a, #132238);` with `background: linear-gradient(180deg, #1e1b4b, #312e81);`.

Verify: `grep -c "linear-gradient(180deg, #1e1b4b, #312e81);" src/main/resources/static/css/app.css` → expected `2`.

- [ ] **Step 4: Hero banner gradient (dashboard)**

Run: `grep -n "background: linear-gradient(135deg, #0f6cbd, #14b8a6);" src/main/resources/static/css/app.css`

Expected: 1 match, inside `.hero-banner`.

Replace with: `background: linear-gradient(135deg, #6366F1, #059669);`

- [ ] **Step 5: `.metric-tone-total` icon tile**

Run: `grep -n "\.metric-tone-total \.dashboard-metric-icon" src/main/resources/static/css/app.css`

Expected: 1 match: `.metric-tone-total .dashboard-metric-icon { background: #dbeafe; color: #0f6cbd; }`

Replace with: `.metric-tone-total .dashboard-metric-icon { background: #E0E7FF; color: #4338CA; }`

- [ ] **Step 6: Site-visit card accent bar**

Run: `grep -n "background: linear-gradient(180deg, #0f6cbd, #14b8a6);" src/main/resources/static/css/app.css`

Expected: 1 match, inside `.site-visit-card::before`.

Replace with: `background: linear-gradient(180deg, #6366F1, #059669);`

- [ ] **Step 7: Autosuggest ticket-avatar tint**

Run: `sed -n '833,836p' src/main/resources/static/css/app.css`

Expected:

```css
.autosuggest-avatar.ticket-avatar {
    background: linear-gradient(135deg, #eff6ff, #dcfce7);
    color: #0f6cbd;
}
```

Replace with:

```css
.autosuggest-avatar.ticket-avatar {
    background: linear-gradient(135deg, #EEF0FF, #D1FAE5);
    color: #4338CA;
}
```

- [ ] **Step 8: Auth banner gradient (Login/Register/Reset/Verify screens)**

Run: `grep -n "linear-gradient(160deg, #0f172a 0%, #0f6cbd 55%, #14b8a6 100%);" src/main/resources/static/css/app.css`

Expected: 1 match, inside `.auth-banner`.

Replace with: `background: linear-gradient(160deg, #1e1b4b 0%, #6366F1 55%, #059669 100%);`

- [ ] **Step 9: Full-file verification — no old brand hex remain**

Run: `grep -n "0f6cbd\|14b8a6\|0f172a, #132238" src/main/resources/static/css/app.css`

Expected: no output. (The one remaining `#0f172a` inside the now-updated auth-banner three-stop gradient was already replaced in Step 8 — this grep confirms no stragglers anywhere in the file.)

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/static/css/app.css
git commit -m "feat(design-system): retint hardcoded gradients and tints to indigo/emerald"
```

---

### Task 5: Remap ticket status and priority pill colors

**Files:**
- Modify: `src/main/resources/static/css/app.css:1100-1113`

**Interfaces:**
- Consumes: `--status-neutral-bg/-fg`, `--status-progress-bg/-fg`, `--status-waiting-bg/-fg`, `--status-positive-bg/-fg`, `--status-negative-bg/-fg`, `--priority-low/medium/high/critical-bg/-fg` from Task 1.
- Produces: `.status-pill.<STATUS>` and `.priority-pill.<LEVEL>` rules — consumed as-is by `tickets.html` (`<span class="status-pill ${ticket.status}">`) and any other screen using the same pattern; no markup changes needed, this is purely a recolor of an existing component.

- [ ] **Step 1: Confirm current block**

Run: `sed -n '1100,1113p' src/main/resources/static/css/app.css`

Expected:

```css
.status-pill.LEADS { background: #f3e8ff; color: #7e22ce; }
.status-pill.SITE_VISITED { background: #cffafe; color: #0f766e; }
.status-pill.OPEN, .status-pill.IN_PROGRESS { background: #dbeafe; color: #1d4ed8; }
.status-pill.ON_HOLD { background: #fef3c7; color: #b45309; }
.status-pill.FOLLOW_UP { background: #fff7ed; color: #c2410c; }
.status-pill.SITE_REVISIT { background: #f0fdf4; color: #166534; }
.status-pill.QUOTED { background: #e0f2fe; color: #0284c7; }
.status-pill.RESOLVED, .status-pill.CLOSED { background: #dcfce7; color: #15803d; }
.status-pill.CANCELLED { background: #f3f4f6; color: #6b7280; }

.priority-pill.LOW { background: #e0f2fe; color: #0369a1; }
.priority-pill.MEDIUM { background: #e9d5ff; color: #7e22ce; }
.priority-pill.HIGH { background: #fed7aa; color: #c2410c; }
.priority-pill.CRITICAL { background: #fecaca; color: #b91c1c; }
```

- [ ] **Step 2: Replace with the semantic-group token mapping**

Replace the exact block from Step 1 with:

```css
.status-pill.LEADS,
.status-pill.OPEN,
.status-pill.SITE_VISITED {
    background: var(--status-neutral-bg);
    color: var(--status-neutral-fg);
}

.status-pill.IN_PROGRESS,
.status-pill.QUOTED {
    background: var(--status-progress-bg);
    color: var(--status-progress-fg);
}

.status-pill.ON_HOLD,
.status-pill.FOLLOW_UP,
.status-pill.SITE_REVISIT {
    background: var(--status-waiting-bg);
    color: var(--status-waiting-fg);
}

.status-pill.RESOLVED,
.status-pill.CLOSED {
    background: var(--status-positive-bg);
    color: var(--status-positive-fg);
}

.status-pill.CANCELLED {
    background: var(--status-negative-bg);
    color: var(--status-negative-fg);
}

.priority-pill.LOW {
    background: var(--priority-low-bg);
    color: var(--priority-low-fg);
}

.priority-pill.MEDIUM {
    background: var(--priority-medium-bg);
    color: var(--priority-medium-fg);
}

.priority-pill.HIGH {
    background: var(--priority-high-bg);
    color: var(--priority-high-fg);
}

.priority-pill.CRITICAL {
    background: var(--priority-critical-bg);
    color: var(--priority-critical-fg);
}
```

- [ ] **Step 3: Verify all 11 status values and 4 priority values are still covered**

Run: `grep -oE "status-pill\.[A-Z_]+" src/main/resources/static/css/app.css | sort -u`

Expected (all 11 `TicketStatus` enum values present): `status-pill.CANCELLED`, `status-pill.CLOSED`, `status-pill.FOLLOW_UP`, `status-pill.IN_PROGRESS`, `status-pill.LEADS`, `status-pill.ON_HOLD`, `status-pill.OPEN`, `status-pill.QUOTED`, `status-pill.RESOLVED`, `status-pill.SITE_REVISIT`, `status-pill.SITE_VISITED`.

Run: `grep -oE "priority-pill\.[A-Z]+" src/main/resources/static/css/app.css | sort -u`

Expected (all 4 `TicketPriority` enum values present): `priority-pill.CRITICAL`, `priority-pill.HIGH`, `priority-pill.LOW`, `priority-pill.MEDIUM`.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/app.css
git commit -m "feat(design-system): remap status/priority pill colors to semantic token groups"
```

---

### Task 6: Dense-table pattern (row hover + sticky-header utility)

The spec's Core Components section commits Phase 0 to defining a dense-data table pattern (row hover, sortable-header affordance, sticky header where useful), reused by ticket lists, staff billing, and admin tables. `.sortable-header` (cursor/transition) and `.table thead th` (muted uppercase header text) already exist in `app.css:1074-1080` and `app.css:1082-1087` — the one missing, safe-to-add-globally piece is row hover. Sticky header is explicitly "where useful" (not every table needs it, and it depends on each screen's scroll container), so it's added as an opt-in utility class here — Phase 1+ screens add the class to the specific tables that need it, rather than this task guessing which ones do.

**Files:**
- Modify: `src/main/resources/static/css/app.css` (append new rules after the `.status-pill`/`.priority-pill` block from Task 5, i.e. after line ~1149 in the pre-Task-5 numbering — locate by the marker in Step 1)

**Interfaces:**
- Consumes: `--primary-soft`, `--panel-strong`, `--duration-fast`, `--ease-out` from Task 1.
- Produces: `.data-table` (row-hover-enabled table wrapper class) and `.table-sticky-header` (opt-in sticky-header utility) — both net-new classes, applied to `<table>` markup by Phase 1+ screen redesigns, not by this task.

- [ ] **Step 1: Confirm the insertion point**

Run: `grep -n "^\.priority-pill\.CRITICAL {$" src/main/resources/static/css/app.css`

Expected: one match — insert the new rules immediately after that rule block's closing `}` (i.e., right before the blank line that precedes `.auth-wrap {`).

- [ ] **Step 2: Add the dense-table pattern**

Insert immediately after the `.priority-pill.CRITICAL { ... }` block:

```css

.data-table tbody tr {
    transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table tbody tr:hover {
    background-color: var(--primary-soft);
}

.table-sticky-header thead th {
    position: sticky;
    top: 0;
    z-index: 2;
    background: var(--panel-strong);
}
```

- [ ] **Step 3: Verify**

Run: `grep -n "^\.data-table tbody tr" src/main/resources/static/css/app.css`

Expected: two matches (base rule and `:hover` rule).

Run: `grep -n "^\.table-sticky-header" src/main/resources/static/css/app.css`

Expected: one match.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/app.css
git commit -m "feat(design-system): add dense-table row-hover pattern and sticky-header utility"
```

---

### Task 7: Manual verification across screens and roles

No automated frontend test suite exists in this project (confirmed in `PROJECT.md` §9) — verification is manual, in-browser, per the spec's Verification Approach.

**Files:** none (verification only, no code changes)

- [ ] **Step 1: Start the app**

Run: `./start-app.sh`

Wait for it to report the containers are up, then run: `docker compose ps` to confirm the app container is healthy.

- [ ] **Step 2: Verify the Login screen (unauthenticated, auth-panel layout)**

Open `http://localhost:9090/login` in a browser.

Check:
- Page background and auth banner render in the new indigo→emerald gradient (not the old blue→teal).
- The "Sign in" button is indigo (`--primary`), not the old blue.
- No visual glitches (unstyled Bootstrap default blue leaking through, broken layout, missing font — Inter should be visibly different from the system-font fallback used before).
- Open browser DevTools console — confirm no 404s for the Google Fonts request or `app.css`.

- [ ] **Step 3: Verify an authenticated staff screen (sidebar + topbar shell)**

Log in with `admin@example.com` / `Admin@123` (seeded in `DataInitializer.java`).

Check on `/dashboard`:
- Sidebar renders the new dark indigo gradient (not navy).
- Active/hover nav items still highlight correctly (role/feature-gated items still show — Admin should see the Admin nav section).
- Hero banner (if present) and metric tiles render in the new palette.
- Headings render in Manrope (visibly different letterforms from Inter body text).

Check on `/tickets/all` (or any ticket list): status pills and priority pills render in the new 5-group / 4-step colors, and are still readable (no white-on-white or low-contrast text).

- [ ] **Step 4: Verify role-gated nav still works for a non-admin role**

Log out, log in with `vendor@example.com` / `Vendor@123`.

Check: vendor sees only their gated nav items (no Admin section, "My Tickets" instead of the staff ticket subnav) — confirms Task 1-5's visual changes didn't touch the `currentFeatures.contains(...)` / `currentRoles.contains(...)` logic in `fragments/layout.html`, which received no edits in this plan.

- [ ] **Step 5: Spot-check an alert/toast**

Trigger any action that shows an inline or toast alert (e.g. submit the login form with a wrong password to see the danger alert, or trigger a 403 by visiting an admin-only URL as the vendor user to see `showAppAlert`).

Check: alert renders with the new accent/danger/warning/info tints, not the old teal/blue tints.

- [ ] **Step 6: Record the result**

If all checks pass, this phase is done — no commit needed for this task (verification only). If any check fails, fix the specific CSS rule involved (identify it via DevTools "Inspect Element" to find the selector) and re-run the relevant step from Tasks 1-6 before proceeding to Phase 1 (Login screen redesign).

---

## Post-Plan Note

This plan intentionally does **not** touch template markup (beyond the one `<link>` addition in Task 3) — form labels, table density, empty-state copy, and other screen-specific UX fixes named in the spec are Phase 1+ work, done one screen at a time starting with Login, per the approved spec's Screen Redesign Order.
