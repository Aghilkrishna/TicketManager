# Dashboard Metrics Count Mismatch - Fix Report

## Issue Summary
The dashboard API showed 4 OPEN tickets, but the list endpoint with `assignedOnly=true` showed only 1 OPEN ticket.

## Root Cause
The dashboard's `countStatusMap()` method was using `assignedOnly=false` when counting tickets for non-admin users, while the list endpoint defaults to `assignedOnly=true`.

### What Each Filter Includes:

**`assignedOnly=false` (old dashboard behavior):**
- Tickets **created by** the user, OR
- Tickets **assigned to** the user, OR
- Tickets where user is in **serviceUsers**

**`assignedOnly=true` (new dashboard behavior, matches list endpoint):**
- Tickets **assigned to** the user, OR
- Tickets where user is in **serviceUsers**

## Solution Implemented

### 1. Updated `TicketService.countStatusMap()` Method
- Added an overloaded version that accepts `assignedOnly` parameter
- Kept backward compatibility with the original method (defaults to `assignedOnly=true`)
- Method signature: `countStatusMap(String username, boolean allTicketScope, boolean assignedOnly)`

### 2. Updated `DashboardRestController.metrics()` Method
- Now passes the correct `assignedOnly` value based on scope:
  - When viewing **all tickets** (`allTicketScope=true`): Uses `assignedOnly=false`
  - When viewing **my tickets** (`allTicketScope=false`): Uses `assignedOnly=true`
- This ensures consistency with the list endpoint behavior

## Result
Now the dashboard metrics will show the same ticket counts as the list endpoint when using the same filters:
- `/api/dashboard/metrics?scope=mine` → Shows same tickets as `/tickets?assignedOnly=true`
- `/api/dashboard/metrics?scope=all` → Shows all tickets (admin only)

## Files Changed
1. `/src/main/java/com/example/ticketmanager/service/TicketService.java`
   - Added overloaded `countStatusMap()` method with `assignedOnly` parameter

2. `/src/main/java/com/example/ticketmanager/controller/api/DashboardRestController.java`
   - Updated to pass correct `assignedOnly` value to `countStatusMap()`

## Testing Steps
1. Restart the application
2. Call the dashboard API: `GET /api/dashboard/metrics?scope=mine`
3. Note the status counts
4. Call the list API with same filters: `GET /tickets?assignedOnly=true`
5. Verify the counts match for each status

## Example Scenario
Before fix:
- Dashboard: 4 OPEN tickets (includes 3 created by user, not assigned)
- List with `assignedOnly=true`: 1 OPEN ticket (only assigned)

After fix:
- Dashboard: 1 OPEN ticket (same filter logic as list)
- List with `assignedOnly=true`: 1 OPEN ticket (same)

