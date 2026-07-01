# Staff Billing — Technician Payment Integration

**Date:** 2026-07-01
**Status:** Approved

## Context

The ticket payment feature added a `TicketPayment` entity with three per-ticket payment records: CLIENT, TECHNICIAN, and VENDOR. Each record carries `expectedPrice`, `actualPrice`, `paymentMode`, `paymentDatetime`, and `status`.

The staff billing feature previously derived its billing amount from `ticket.actualCost` / `ticket.estimatedCost`. These fields are now secondary — the canonical payment data lives in `TicketPayment`. Staff billing must be updated to use the TECHNICIAN payment record as its primary source.

## Decisions

| # | Decision |
|---|----------|
| 1 | Billing amount is sourced from the **TECHNICIAN** payment type only |
| 2 | Amount resolution order: `actualPrice` → `expectedPrice` → `ticket.actualCost` → `ticket.estimatedCost` → `ZERO` |
| 3 | Fallback to old cost fields is retained for backward compatibility with tickets created before the payment feature |
| 4 | Payment mode, payment date, and payment status are surfaced in the billing details view and invoice |

## Backend Changes

### `StaffBillingService.resolveBillAmount(Ticket)`

Replace the current `actualCost` → `estimatedCost` logic with:

```
ticket.getPayments()
  .stream()
  .filter(p -> p.getPaymentType() == TicketPaymentType.TECHNICIAN)
  .findFirst()
  → if present:
      actualPrice  (if non-null)
      expectedPrice (if non-null)
  → if absent or both null:
      ticket.getActualCost()
      ticket.getEstimatedCost()
      BigDecimal.ZERO
```

A private helper `getTechnicianPayment(Ticket)` extracts the optional `TicketPayment`.

### `AdminDtos.StaffBillingTicketLine`

Add three nullable fields:

```java
String paymentMode        // "CASH" | "BANK_TRANSFER" | "UPI" | null
LocalDateTime paymentDatetime  // null if not recorded
String paymentStatus      // free-text from TicketPayment.status, null if absent
```

### `StaffBillingService.getStaffBillingDetails()`

When constructing each `StaffBillingTicketLine`, look up the TECHNICIAN payment for the ticket and pass the three new fields. All three are nullable — if no TECHNICIAN payment exists, they are `null`.

### `StaffBillingService.listStaffBillingSummaries()`

No DTO change. The `BillingAccumulator.add()` calls `resolveBillAmount()`, so the fix flows through automatically.

## Frontend Changes

### `admin-staff-billing-details.html`

Add three columns to the ticket line table after the existing "Price" column:

| Column | Source | Null display |
|--------|--------|--------------|
| Payment Mode | `ticket.paymentMode` formatted (Cash / Bank Transfer / UPI) | — |
| Payment Date | `ticket.paymentDatetime` formatted `dd MMM yyyy, hh:mm a` | — |
| Payment Status | `ticket.paymentStatus` as a text badge | — |

### `admin-staff-billing-invoice.html`

Add two columns after the "Amount" column:

| Column | Source | Null display |
|--------|--------|--------------|
| Payment Mode | formatted label | — |
| Payment Date | formatted datetime | — |

Payment status is omitted from the invoice — the billing status badge already conveys paid/unpaid context there.

### `admin-staff-billing.html`

No template change. Aggregate amounts update automatically via the service fix.

## Out of Scope

- CLIENT and VENDOR payment types are not surfaced in staff billing.
- `TicketBillingStatus` (PAID/UNPAID on the Ticket entity) is unchanged — it remains the admin-controlled billing flag.
- No changes to the summary list DTO (`StaffBillingSummary`).
