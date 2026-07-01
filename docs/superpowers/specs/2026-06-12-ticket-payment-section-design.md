---
name: ticket-payment-section-design
description: Design for upgrading the ticket price section to 3 payment subsections (Client, Technician, Vendor) with role-based access
metadata:
  type: spec
  date: 2026-06-12
---

# Ticket Payment Section Upgrade

## Overview

Replace the existing simple "Price Section" on ticket create/edit/view pages with a structured **Payment Section** containing 3 subsections: Client Payment, Technician Payment, and Vendor Payment. Each subsection is a separate row in a new `ticket_payments` table. Existing price fields on the `Ticket` entity are kept in the database but hidden from the UI.

---

## Data Model

### New Enums

**`TicketPaymentType`**: `CLIENT`, `TECHNICIAN`, `VENDOR`

**`TicketPaymentMode`**: `CASH`, `BANK_TRANSFER`, `UPI`

### New Entity: `TicketPayment` (table: `ticket_payments`)

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `id` | bigint | NO | auto-increment PK |
| `ticket_id` | bigint | NO | FK → `tickets.id` |
| `payment_type` | varchar(20) | NO | CLIENT / TECHNICIAN / VENDOR |
| `expected_price` | decimal(12,2) | YES | |
| `actual_price` | decimal(12,2) | YES | |
| `payment_mode` | varchar(20) | YES | CASH / BANK_TRANSFER / UPI |
| `payment_datetime` | datetime | YES | |
| `status` | varchar(50) | YES | CLIENT: PENDING, PAID_TO_YUBIX, PAID_TO_TECHNICIAN, PAID_TO_VENDOR; TECHNICIAN/VENDOR: PENDING, PAID |

### `Ticket` Entity Change

Add `@OneToMany(mappedBy="ticket", cascade=ALL, orphanRemoval=true) List<TicketPayment> payments`.

Existing fields kept in DB, hidden from UI: `estimatedCost`, `actualCost`, `pricingModel`, `billingStatus`, `billingPaidAt`, `additionalNotes`.

---

## Backend

### New Files

- `TicketPaymentType.java` — enum: CLIENT, TECHNICIAN, VENDOR
- `TicketPaymentMode.java` — enum: CASH, BANK_TRANSFER, UPI
- `TicketPayment.java` — `@Entity`, `@ManyToOne Ticket ticket`, 5 payment fields
- `TicketPaymentRepository.java` — `JpaRepository<TicketPayment, Long>`, method: `findByTicketId(Long ticketId)`
- `V13__add_ticket_payments_table.sql` — Flyway migration

### Modified Files

**`Ticket.java`**: add `List<TicketPayment> payments` with cascade.

**`TicketService.java`**: add `saveOrUpdatePayments(Ticket ticket, PaymentFields fields)` helper that:
1. For each type (CLIENT, TECHNICIAN, VENDOR), find existing row by ticket+type or create new
2. Set fields only if at least one field is non-null/non-blank (skip empty subsections)
3. Save via repository or cascade through ticket

**`TicketRestController.java`** (or MVC controller): extend create/update request to include 15 flat payment fields:
- `clientExpectedPrice`, `clientActualPrice`, `clientPaymentMode`, `clientPaymentDatetime`, `clientPaymentStatus`
- `technicianExpectedPrice`, `technicianActualPrice`, `technicianPaymentMode`, `technicianPaymentDatetime`, `technicianPaymentStatus`
- `vendorExpectedPrice`, `vendorActualPrice`, `vendorPaymentMode`, `vendorPaymentDatetime`, `vendorPaymentStatus`

---

## Frontend

### Role-Based Visibility

| Subsection | Admin | Technician | Vendor |
|---|---|---|---|
| Client Payment | ✓ | ✓ | ✗ |
| Technician Payment | ✓ | ✓ | ✗ |
| Vendor Payment | ✓ | ✗ | ✓ |

Thymeleaf conditions (using existing template variables):
- Client + Technician cards (view): `th:if="${adminTicketScope or agentViewer}"`
- Client + Technician cards (edit): `th:if="${adminTicketScope or agentEditorView}"`
- Vendor card (view): `th:if="${adminTicketScope or vendorOnlyViewer}"`
- Vendor card (edit): `th:if="${adminTicketScope or vendorOnlyEditor}"`

### Create / Edit Pages

Replace the existing Price Section with a Payment Section inside the same `col-xl-5` container. Three compact cards stacked vertically, each with:
- Header: subsection name (e.g., "Client Payment")
- Row 1: Expected Price (col-md-6) + Actual Price (col-md-6)
- Row 2: Mode dropdown (col-md-6) + Status dropdown (col-md-6)
- Row 3: Payment Datetime (col-12)

Form field names follow flat naming: `clientExpectedPrice`, `clientPaymentMode`, etc.

### View Page

Replace Price Section with read-only Payment Section. Each subsection displayed as label/value pairs. Status rendered as a colored badge:
- PENDING → yellow (`badge bg-warning text-dark`)
- PAID → green (`badge bg-success`)
- PAID_TO_YUBIX / PAID_TO_TECHNICIAN / PAID_TO_VENDOR → blue (`badge bg-primary`)

---

## Migration

**`V13__add_ticket_payments_table.sql`**:

```sql
CREATE TABLE ticket_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    payment_type VARCHAR(20) NOT NULL,
    expected_price DECIMAL(12,2),
    actual_price DECIMAL(12,2),
    payment_mode VARCHAR(20),
    payment_datetime DATETIME,
    status VARCHAR(50),
    CONSTRAINT fk_tp_ticket FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
);
```

---

## Scope Boundaries

- `pricingModel` and `additionalNotes` hidden from UI, kept in DB for future use
- No migration of existing `estimatedCost`/`actualCost` data into new table (they remain as legacy columns)
- Payment section only visible to roles with `adminTicketScope`, `technicianView`, or `vendorView` — same as existing price section gating
