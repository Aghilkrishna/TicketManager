# Frontend Redesign — Phase 0: Design System Foundation

**Date:** 2026-07-22
**Status:** Approved
**Branch:** develop_30062026

---

## Overview

TicketManager's 14 screens (Login, Dashboard, Ticket List, Ticket Details, Create Ticket, Edit Ticket, Customer Management, Technician Management, Vendor Management, Reports, Settings, User Profile, Notifications, Admin Panel) are being redesigned incrementally toward a unified, modern SaaS look, while preserving every existing backend integration, API call, and business workflow. No backend code is modified as part of this redesign.

Because a 14-screen redesign is really 14+ sub-projects that must share one visual foundation, the work is split into:

- **Phase 0 (this spec)** — establish the design system: tokens, core reusable components, and the app-shell reskin approach. Ships once, affects all screens immediately (see Rollout Mechanics).
- **Phase 1+** — one spec + plan + implementation cycle per screen, in the order above, each consuming Phase 0's foundation instead of re-deciding tokens/components per screen.

## Current State (baseline)

- Server-rendered Thymeleaf templates (`src/main/resources/templates/`), Bootstrap 5.3.3 + Bootstrap Icons 1.11.3 loaded via CDN in `fragments/layout.html :: head`, plus a custom `src/main/resources/static/css/app.css` (~37KB) defining an existing partial token set (`--bg`, `--panel`, `--primary`, `--accent`, `--shadow`, etc.) and a glassmorphic panel aesthetic.
- Shared app shell: collapsible dark sidebar + topbar (`fragments/layout.html :: sidebar` / `:: topbar`), nav items gated by `currentFeatures.contains('FEATURE_...')` / `currentRoles.contains('ROLE_...')` — role/feature logic stays untouched.
- Auth screens (`login.html`, `register.html`, `vendor-login.html`, etc.) use a separate `auth-wrap` / `auth-panel` layout, not the sidebar shell.
- Frontend logic is inline `<script>` blocks calling existing `/api/**` REST endpoints via `fetch` (e.g. `login.html` → `POST /api/auth/login`), plus shared helpers in `static/js/app.js` (`setButtonLoading`, `showInlineAlert`, `clearInlineAlert`). No JS framework, no build step.
- No automated frontend test suite exists — verification is manual, in-browser.

## Goals

1. Produce a coherent, distinctive "Indigo Modern SaaS" visual identity, replacing the current blue/teal glass-panel look.
2. Fix concrete, screen-level UX issues as each screen is redesigned (e.g. placeholder-only form labels, inconsistent status/priority visualization, ad-hoc alert styling) — not just a visual reskin.
3. Preserve 100% of existing functionality, API calls, role/feature gating, and backend contracts.
4. Keep implementation effort proportionate: reuse Bootstrap 5 structurally, avoid introducing a JS framework or build tooling.

## Non-Goals

- No backend/API/controller/entity changes of any kind.
- No change to authentication, routing, or role/feature permission logic.
- No navigation-architecture change (sidebar + topbar shell stays structurally as-is).
- No migration away from Bootstrap 5 utility/grid/form classes.
- No icon library swap (Bootstrap Icons stays).
- No automated test suite introduced (matches existing project convention — manual verification per screen).

---

## Visual Direction: Indigo Modern SaaS

Chosen over "Navy & Slate Professional" (safer, closer to current look) and "Dark Ops Console" (dark-first, higher risk for customer/vendor-facing screens) for being distinctive and current while staying fully professional and light/dark-background flexible.

### Design Tokens

Extend/replace the `:root` block in `app.css` (same mechanism, no new tooling):

**Color:**
| Token | Value | Use |
|---|---|---|
| `--primary` | `#6366F1` | Primary actions, active nav, links |
| `--primary-hover` | `#4F46E5` | Hover/active state of primary |
| `--primary-soft` | `#EEF0FF` | Primary-tinted backgrounds (badges, selected rows) |
| `--accent` | `#059669` | Success / positive actions — deliberately distinct from primary so CTAs and "success" states don't visually collide |
| `--bg` | `#F5F3FF`-derived neutral scale | Page background |
| `--surface` | `#FFFFFF` | Cards, panels |
| `--surface-strong` | `#FFFFFF` (higher elevation) | Modals, popovers |
| `--border` | light neutral, low-contrast | Dividers, card borders |
| `--text` / `--text-muted` | dark neutral / mid neutral | Primary / secondary text (WCAG AA, 4.5:1 minimum) |
| `--warning` | `#F59E0B` | Warning/on-hold states |
| `--danger` | `#DC2626` | Destructive actions, cancelled state |

**Status color mapping** (not 11 unique hues — grouped semantically so ticket status pills stay scannable):
- Early/neutral (LEADS, OPEN, SITE_VISITED): neutral/blue
- In-progress-family (IN_PROGRESS, QUOTED): primary indigo
- Waiting (ON_HOLD, FOLLOW_UP, SITE_REVISIT): amber/`--warning`
- Terminal-positive (RESOLVED, CLOSED): emerald/`--accent`
- Terminal-negative (CANCELLED): `--danger`

`TicketPriority` (LOW/MEDIUM/HIGH/CRITICAL) gets its own 4-step scale from neutral → `--danger`.

**Typography:** Inter (UI/body) + Manrope (headings), loaded via Google Fonts CDN in the shared `head` fragment (same CDN-loading pattern already used for Bootstrap/Bootstrap Icons). `font-variant-numeric: tabular-nums` on data columns (prices, dates, counts, IDs) for clean alignment in ticket tables and billing views.

**Elevation / radius / spacing / motion:** fixed scales replacing today's ad-hoc per-component values —
- Shadows: `--shadow-flat: none` (dense table rows), `--shadow-card: 0 1px 3px rgba(15,23,42,0.08), 0 1px 2px rgba(15,23,42,0.06)`, `--shadow-modal: 0 20px 40px rgba(15,23,42,0.16)`
- Radius: `--radius-control: 8px` (buttons, inputs, badges), `--radius-card: 14px` (cards, modals)
- Spacing scale: `--space-1: 4px` through `--space-8: 32px` in 4px steps, used for padding/gap/margin instead of arbitrary values
- Motion: `--duration-fast: 150ms`, `--duration-base: 200ms`, both `ease-out`; respects `prefers-reduced-motion`

**Icons:** keep Bootstrap Icons (`bi bi-*`) — used extensively across every existing template; switching sets is pure churn given the "keep Bootstrap" decision.

### Core Components & Patterns

Defined once in `app.css`, reused by every screen redesign:

- **Buttons** — primary (indigo fill), secondary (outline/ghost), destructive (red), loading state (reuses existing `setButtonLoading()` JS helper unchanged — CSS-only work).
- **Inputs & forms** — visible `<label>` per field (fixes placeholder-only inputs like today's Login form), inline validation error placement below field, persistent helper text pattern for complex inputs.
- **Cards/panels** — surface hierarchy: flat surface for dense data areas (tables), elevated card for forms/summaries/modals — replacing the single "glass panel" treatment used everywhere today.
- **Status pills/badges** — one reusable component for `TicketStatus` (11 values) and `TicketPriority` (4 values) using the semantic mapping above; reused in ticket list, detail, dashboard, and admin views.
- **Tables** — dense-data pattern: row hover, sortable-header affordance, sticky header where useful. Shared by ticket lists, staff billing, and admin user/role tables.
- **Empty states & alerts** — one empty-state pattern (no tickets / no results + guidance), one alert/toast pattern replacing the current ad-hoc `showInlineAlert()` / `#message` div styling (JS hook unchanged, only visual treatment changes).

Modals, file-upload/dropzone (attachments, ID-proofs), and chat-bubble styling are intentionally **not** finalized here — decided when their owning screen (Ticket Details / Vendor Management / Notifications, respectively) is redesigned, using the same token system.

### App Shell

- Sidebar/topbar structure in `fragments/layout.html` stays functionally identical (collapsible, role/feature-gated via `currentFeatures.contains(...)` / `currentRoles.contains(...)`). Visual treatment moves from the current navy gradient (`#0f172a` → `#132238`) to an indigo-tinted dark sidebar, with nav active/hover states on the new token system.
- Auth screens (`auth-wrap`/`auth-panel` pattern) get their own visual pass under the same tokens as part of the Login screen cycle (Phase 1), since they don't use the sidebar shell.

### Rollout Mechanics

Tokens live in `app.css`, loaded globally via the shared `head` fragment — updating them shifts colors/fonts/buttons/cards across **every** screen at once, before screen-specific markup is touched. This is intentional: it keeps the app visually coherent instead of half old/half new. Deeper per-screen fixes (form labels, table density, empty states, etc.) still roll out one screen at a time, in the stated order, after Phase 0 ships.

### Verification Approach

No automated frontend test suite exists (matches project convention noted in `PROJECT.md` §9). Each phase (Phase 0, and each subsequent screen) is verified manually: start the app locally, click through the affected screen(s) in-browser, check the golden path plus at least one role variation (e.g. vendor vs. staff nav visibility) before considering the phase done.

---

## Screen Redesign Order (Phase 1+)

1. Login
2. Dashboard
3. Ticket List
4. Ticket Details
5. Create Ticket
6. Edit Ticket
7. Customer Management
8. Technician Management
9. Vendor Management
10. Reports
11. Settings
12. User Profile
13. Notifications
14. Admin Panel

Each gets its own short spec (Phase 0 has already absorbed the systemic decisions), its own implementation plan, and its own review checkpoint before the next screen begins.
